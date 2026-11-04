package app.settlezero.core.data.repository

import androidx.room.withTransaction
import app.settlezero.core.common.Result
import app.settlezero.core.common.TimeProvider
import app.settlezero.core.data.sync.CreateExpensePayload
import app.settlezero.core.data.sync.DeleteExpensePayload
import app.settlezero.core.data.sync.ExpenseSplitAllocationPayload
import app.settlezero.core.data.sync.SyncOperationTypes
import app.settlezero.core.database.SettleZeroDatabase
import app.settlezero.core.database.model.ActivityHistoryEntity
import app.settlezero.core.database.model.ExpenseEntity
import app.settlezero.core.database.model.ExpenseSplitEntity
import app.settlezero.core.database.model.SyncQueueEntity
import app.settlezero.core.database.model.asDomain
import app.settlezero.core.domain.TransactionValidator
import app.settlezero.core.domain.ValidationResult
import app.settlezero.core.model.Expense
import app.settlezero.core.model.ExpenseSplitAllocation
import app.settlezero.core.model.SplitType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

interface ExpenseRepository {
    fun observeExpenses(groupId: String): Flow<List<Expense>>
    fun observeActiveExpenses(groupId: String): Flow<List<Expense>>
    suspend fun getExpenses(groupId: String): List<Expense>
    suspend fun getActiveExpenses(groupId: String): List<Expense>
    suspend fun getExpense(expenseId: String): Expense?
    suspend fun createExpense(
        groupId: String,
        description: String,
        amountMinor: Long,
        payerMemberId: String,
        createdByMemberId: String,
        expenseDate: Long,
        splitType: SplitType,
        allocations: List<ExpenseSplitAllocation>,
        note: String? = null
    ): Result<Expense>
    suspend fun deleteExpense(expenseId: String, deletedByMemberId: String): Result<Unit>
}

class OfflineFirstExpenseRepository(
    private val database: SettleZeroDatabase,
    private val timeProvider: TimeProvider,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ExpenseRepository {

    private val expenseDao = database.expenseDao()
    private val groupDao = database.groupDao()
    private val memberDao = database.memberDao()
    private val syncQueueDao = database.syncQueueDao()
    private val historyDao = database.activityHistoryDao()

    override fun observeExpenses(groupId: String): Flow<List<Expense>> =
        expenseDao.observeExpenses(groupId).map { list -> list.map { it.asDomain() } }

    override fun observeActiveExpenses(groupId: String): Flow<List<Expense>> =
        expenseDao.observeActiveExpenses(groupId).map { list -> list.map { it.asDomain() } }

    override suspend fun getExpenses(groupId: String): List<Expense> =
        expenseDao.getExpenses(groupId).map { it.asDomain() }

    override suspend fun getActiveExpenses(groupId: String): List<Expense> =
        expenseDao.getActiveExpenses(groupId).map { it.asDomain() }

    override suspend fun getExpense(expenseId: String): Expense? =
        expenseDao.getExpenseById(expenseId)?.asDomain()

    override suspend fun createExpense(
        groupId: String,
        description: String,
        amountMinor: Long,
        payerMemberId: String,
        createdByMemberId: String,
        expenseDate: Long,
        splitType: SplitType,
        allocations: List<ExpenseSplitAllocation>,
        note: String?
    ): Result<Expense> {
        val group = groupDao.getGroupById(groupId)
            ?: return Result.Error(IllegalArgumentException("Group does not exist: $groupId"))

        if (group.status == "ARCHIVED") {
            return Result.Error(IllegalStateException("Cannot add expenses to an archived group."))
        }

        val members = memberDao.getMembers(groupId).associate { it.id to it.asDomain() }
        val now = timeProvider.currentTimeMillis()
        val expenseId = UUID.randomUUID().toString()

        val expense = Expense(
            id = expenseId,
            groupId = groupId,
            description = description.trim(),
            amountMinor = amountMinor,
            payerMemberId = payerMemberId,
            createdByMemberId = createdByMemberId,
            expenseDate = expenseDate,
            createdAt = now,
            note = note?.trim()?.ifBlank { null },
            splitType = splitType,
            allocations = allocations
        )

        val validation = TransactionValidator.validateExpense(expense, members)
        if (!validation.isValid) {
            val reason = (validation as ValidationResult.Invalid).reason
            return Result.Error(IllegalArgumentException(reason))
        }

        val expenseEntity = ExpenseEntity(
            id = expense.id,
            groupId = groupId,
            description = expense.description,
            amountMinor = expense.amountMinor,
            payerMemberId = payerMemberId,
            createdByMemberId = createdByMemberId,
            expenseDate = expenseDate,
            createdAt = now,
            note = expense.note,
            splitType = splitType.name
        )

        val splitEntities = allocations.map {
            ExpenseSplitEntity(
                id = UUID.randomUUID().toString(),
                expenseId = expenseId,
                memberId = it.memberId,
                allocatedAmountMinor = it.amountMinor
            )
        }

        val payerName = members[payerMemberId]?.name ?: "Member"
        val historyEntity = ActivityHistoryEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            actorMemberId = createdByMemberId,
            eventType = "EXPENSE_ADDED",
            summary = "$payerName paid for '${expense.description}'",
            entityId = expenseId,
            createdAt = now
        )

        val payload = CreateExpensePayload(
            groupId = groupId,
            expenseId = expenseId,
            description = expense.description,
            amountMinor = amountMinor,
            payerMemberId = payerMemberId,
            createdByMemberId = createdByMemberId,
            expenseDate = expenseDate,
            splitType = splitType.name,
            note = expense.note,
            allocations = allocations.map {
                ExpenseSplitAllocationPayload(it.memberId, it.amountMinor)
            },
            createdAt = now
        )

        val syncItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            operationType = SyncOperationTypes.CREATE_EXPENSE,
            payloadJson = json.encodeToString(payload),
            idempotencyKey = UUID.randomUUID().toString(),
            status = "PENDING",
            createdAt = now
        )

        return try {
            database.withTransaction {
                // Permanently lock group currency if not locked
                if (!group.isCurrencyLocked) {
                    groupDao.lockCurrency(groupId, now)
                }
                expenseDao.insertExpenseWithSplits(expenseEntity, splitEntities)
                historyDao.insert(historyEntity)
                syncQueueDao.enqueue(syncItem)
            }
            Result.Success(expense)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteExpense(
        expenseId: String,
        deletedByMemberId: String
    ): Result<Unit> {
        val populated = expenseDao.getExpenseById(expenseId)
            ?: return Result.Error(IllegalArgumentException("Expense not found: $expenseId"))

        val members = memberDao.getMembers(populated.expense.groupId).associate { it.id to it.asDomain() }
        val actor = members[deletedByMemberId]
            ?: return Result.Error(IllegalArgumentException("Actor is not a member of this group: $deletedByMemberId"))

        val isCreator = populated.expense.createdByMemberId == deletedByMemberId
        val isAdmin = actor.isAdmin

        if (!isCreator && !isAdmin) {
            return Result.Error(IllegalAccessException("Only the expense creator or an admin can delete an expense."))
        }

        val now = timeProvider.currentTimeMillis()
        val payload = DeleteExpensePayload(
            groupId = populated.expense.groupId,
            expenseId = expenseId,
            deletedByMemberId = deletedByMemberId,
            deletedAt = now
        )

        val syncItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            groupId = populated.expense.groupId,
            operationType = SyncOperationTypes.DELETE_EXPENSE,
            payloadJson = json.encodeToString(payload),
            idempotencyKey = UUID.randomUUID().toString(),
            status = "PENDING",
            createdAt = now
        )

        return try {
            database.withTransaction {
                expenseDao.softDeleteExpense(expenseId, deletedByMemberId, now)
                historyDao.insert(
                    ActivityHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        groupId = populated.expense.groupId,
                        actorMemberId = deletedByMemberId,
                        eventType = "EXPENSE_DELETED",
                        summary = "${actor.name} deleted '${populated.expense.description}'",
                        entityId = expenseId,
                        createdAt = now
                    )
                )
                syncQueueDao.enqueue(syncItem)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

