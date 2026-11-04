package app.settlezero.core.data.repository

import androidx.room.withTransaction
import app.settlezero.core.common.Result
import app.settlezero.core.common.TimeProvider
import app.settlezero.core.data.sync.CreateGroupPayload
import app.settlezero.core.data.sync.SyncOperationTypes
import app.settlezero.core.database.SettleZeroDatabase
import app.settlezero.core.database.model.ActivityHistoryEntity
import app.settlezero.core.database.model.ExpenseEntity
import app.settlezero.core.database.model.ExpenseSplitEntity
import app.settlezero.core.database.model.GroupEntity
import app.settlezero.core.database.model.MemberEntity
import app.settlezero.core.database.model.SettlementEntity
import app.settlezero.core.database.model.SyncQueueEntity
import app.settlezero.core.database.model.asDomain
import app.settlezero.core.model.ActivityHistory
import app.settlezero.core.model.Expense
import app.settlezero.core.model.ExpenseSplitAllocation
import app.settlezero.core.model.Group
import app.settlezero.core.model.GroupStatus
import app.settlezero.core.model.Member
import app.settlezero.core.model.Settlement
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class GroupExportData(
    val version: String = "1.0",
    val exportedAt: Long,
    val group: Group,
    val members: List<Member>,
    val expenses: List<Expense>,
    val settlements: List<Settlement>,
    val activityHistory: List<ActivityHistory> = emptyList()
)

interface DataPortabilityRepository {
    suspend fun exportGroup(groupId: String): Result<String>
    suspend fun parseGroup(jsonString: String): Result<GroupExportData>
    suspend fun cloneGroup(exportData: GroupExportData, newGroupName: String? = null): Result<Group>
}

class OfflineFirstDataPortabilityRepository(
    private val database: SettleZeroDatabase,
    private val timeProvider: TimeProvider,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
) : DataPortabilityRepository {

    private val groupDao = database.groupDao()
    private val memberDao = database.memberDao()
    private val expenseDao = database.expenseDao()
    private val settlementDao = database.settlementDao()
    private val historyDao = database.activityHistoryDao()
    private val syncQueueDao = database.syncQueueDao()

    override suspend fun exportGroup(groupId: String): Result<String> {
        val group = groupDao.getGroupById(groupId)?.asDomain()
            ?: return Result.Error(IllegalArgumentException("Group $groupId not found"))

        val members = memberDao.getMembers(groupId).map { it.asDomain() }
        val expenses = expenseDao.getExpenses(groupId).map { it.asDomain() }
        val settlements = settlementDao.getSettlements(groupId).map { it.asDomain() }
        val history = historyDao.getHistory(groupId).map { it.asDomain() }

        val exportData = GroupExportData(
            version = "1.0",
            exportedAt = timeProvider.currentTimeMillis(),
            group = group,
            members = members,
            expenses = expenses,
            settlements = settlements,
            activityHistory = history
        )

        return try {
            val jsonString = json.encodeToString(exportData)
            Result.Success(jsonString)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun parseGroup(jsonString: String): Result<GroupExportData> {
        return try {
            val parsed = json.decodeFromString<GroupExportData>(jsonString)
            Result.Success(parsed)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun cloneGroup(
        exportData: GroupExportData,
        newGroupName: String?
    ): Result<Group> {
        val now = timeProvider.currentTimeMillis()
        val newGroupId = UUID.randomUUID().toString()
        val newInviteToken = UUID.randomUUID().toString().replace("-", "").take(12)
        val nameToUse = newGroupName?.takeIf { it.isNotBlank() } ?: "${exportData.group.name} (Clone)"

        // Member ID mapping: old ID -> new ID
        val memberIdMap = exportData.members.associate { it.id to UUID.randomUUID().toString() }

        val clonedGroup = Group(
            id = newGroupId,
            name = nameToUse,
            currencyCode = exportData.group.currencyCode,
            currencyScale = exportData.group.currencyScale,
            status = GroupStatus.ACTIVE,
            inviteToken = newInviteToken,
            isCurrencyLocked = false,
            isReadOnly = false,
            createdAt = now,
            updatedAt = now
        )

        val clonedMembers = exportData.members.map { old ->
            Member(
                id = memberIdMap[old.id] ?: UUID.randomUUID().toString(),
                groupId = newGroupId,
                name = old.name,
                role = old.role,
                status = old.status,
                createdAt = now
            )
        }

        val clonedExpenses = exportData.expenses.map { oldExp ->
            val newExpId = UUID.randomUUID().toString()
            val newPayerId = memberIdMap[oldExp.payerMemberId] ?: ""
            val newCreatorId = memberIdMap[oldExp.createdByMemberId] ?: newPayerId

            val newAllocations = oldExp.allocations.map { oldAlloc ->
                ExpenseSplitAllocation(
                    memberId = memberIdMap[oldAlloc.memberId] ?: "",
                    amountMinor = oldAlloc.amountMinor
                )
            }

            Expense(
                id = newExpId,
                groupId = newGroupId,
                description = oldExp.description,
                amountMinor = oldExp.amountMinor,
                payerMemberId = newPayerId,
                createdByMemberId = newCreatorId,
                expenseDate = oldExp.expenseDate,
                createdAt = now,
                note = oldExp.note,
                splitType = oldExp.splitType,
                isDeleted = oldExp.isDeleted,
                deletedAt = oldExp.deletedAt,
                deletedByMemberId = oldExp.deletedByMemberId?.let { memberIdMap[it] },
                allocations = newAllocations
            )
        }

        val clonedSettlements = exportData.settlements.map { oldSet ->
            Settlement(
                id = UUID.randomUUID().toString(),
                groupId = newGroupId,
                payerMemberId = memberIdMap[oldSet.payerMemberId] ?: "",
                recipientMemberId = memberIdMap[oldSet.recipientMemberId] ?: "",
                amountMinor = oldSet.amountMinor,
                settlementDate = oldSet.settlementDate,
                createdAt = now,
                note = oldSet.note,
                isDeleted = oldSet.isDeleted,
                deletedAt = oldSet.deletedAt,
                deletedByMemberId = oldSet.deletedByMemberId?.let { memberIdMap[it] }
            )
        }

        database.withTransaction {
            // Insert Group
            groupDao.insertGroup(
                GroupEntity(
                    id = clonedGroup.id,
                    name = clonedGroup.name,
                    currencyCode = clonedGroup.currencyCode,
                    currencyScale = clonedGroup.currencyScale,
                    status = clonedGroup.status.name,
                    inviteToken = clonedGroup.inviteToken,
                    isCurrencyLocked = clonedGroup.isCurrencyLocked,
                    isReadOnly = false,
                    createdAt = clonedGroup.createdAt,
                    updatedAt = clonedGroup.updatedAt
                )
            )

            // Insert Members
            val memberEntities = clonedMembers.map { m ->
                MemberEntity(
                    id = m.id,
                    groupId = m.groupId,
                    name = m.name,
                    role = m.role.name,
                    status = m.status.name,
                    createdAt = m.createdAt
                )
            }
            memberDao.insertMembers(memberEntities)

            // Insert Expenses & Splits
            clonedExpenses.forEach { exp ->
                val expenseEntity = ExpenseEntity(
                    id = exp.id,
                    groupId = exp.groupId,
                    description = exp.description,
                    amountMinor = exp.amountMinor,
                    payerMemberId = exp.payerMemberId,
                    createdByMemberId = exp.createdByMemberId,
                    expenseDate = exp.expenseDate,
                    createdAt = exp.createdAt,
                    note = exp.note,
                    splitType = exp.splitType.name,
                    isDeleted = exp.isDeleted,
                    deletedAt = exp.deletedAt,
                    deletedByMemberId = exp.deletedByMemberId
                )
                val splitEntities = exp.allocations.map { alloc ->
                    ExpenseSplitEntity(
                        id = UUID.randomUUID().toString(),
                        expenseId = exp.id,
                        memberId = alloc.memberId,
                        allocatedAmountMinor = alloc.amountMinor
                    )
                }
                expenseDao.insertExpenseWithSplits(expenseEntity, splitEntities)
            }

            // Insert Settlements
            clonedSettlements.forEach { set ->
                settlementDao.insertSettlement(
                    SettlementEntity(
                        id = set.id,
                        groupId = set.groupId,
                        payerMemberId = set.payerMemberId,
                        recipientMemberId = set.recipientMemberId,
                        amountMinor = set.amountMinor,
                        settlementDate = set.settlementDate,
                        createdAt = set.createdAt,
                        note = set.note,
                        isDeleted = set.isDeleted,
                        deletedAt = set.deletedAt,
                        deletedByMemberId = set.deletedByMemberId
                    )
                )
            }

            // Record Activity History for Clone
            historyDao.insert(
                ActivityHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = newGroupId,
                    actorMemberId = clonedMembers.firstOrNull()?.id ?: "",
                    eventType = "GROUP_CREATED",
                    summary = "Cloned group from backup: ${clonedGroup.name}",
                    entityId = newGroupId,
                    createdAt = now
                )
            )

            // Enqueue Group Creation Sync
            val payload = CreateGroupPayload(
                groupId = clonedGroup.id,
                name = clonedGroup.name,
                currencyCode = clonedGroup.currencyCode,
                currencyScale = clonedGroup.currencyScale,
                creatorMemberId = clonedMembers.firstOrNull()?.id ?: "",
                creatorName = clonedMembers.firstOrNull()?.name ?: "",
                inviteToken = clonedGroup.inviteToken,
                createdAt = now
            )
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = newGroupId,
                    operationType = SyncOperationTypes.CREATE_GROUP,
                    payloadJson = json.encodeToString(payload),
                    idempotencyKey = UUID.randomUUID().toString(),
                    status = "PENDING",
                    createdAt = now
                )
            )
        }

        return Result.Success(clonedGroup)
    }
}
