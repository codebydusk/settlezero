package app.settlezero.core.data.repository

import androidx.room.withTransaction
import app.settlezero.core.common.Result
import app.settlezero.core.common.TimeProvider
import app.settlezero.core.data.sync.DeleteSettlementPayload
import app.settlezero.core.data.sync.RecordSettlementPayload
import app.settlezero.core.data.sync.SyncOperationTypes
import app.settlezero.core.database.SettleZeroDatabase
import app.settlezero.core.database.model.ActivityHistoryEntity
import app.settlezero.core.database.model.SettlementEntity
import app.settlezero.core.database.model.SyncQueueEntity
import app.settlezero.core.database.model.asDomain
import app.settlezero.core.domain.DebtSimplifier
import app.settlezero.core.domain.NetBalanceProjector
import app.settlezero.core.domain.TransactionValidator
import app.settlezero.core.domain.ValidationResult
import app.settlezero.core.model.Settlement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

interface SettlementRepository {
    fun observeSettlements(groupId: String): Flow<List<Settlement>>
    fun observeActiveSettlements(groupId: String): Flow<List<Settlement>>
    suspend fun getSettlements(groupId: String): List<Settlement>
    suspend fun getActiveSettlements(groupId: String): List<Settlement>
    suspend fun getSettlement(settlementId: String): Settlement?
    suspend fun recordSettlement(
        groupId: String,
        payerMemberId: String,
        recipientMemberId: String,
        amountMinor: Long,
        settlementDate: Long,
        recordedByMemberId: String,
        note: String? = null
    ): Result<Settlement>
    suspend fun deleteSettlement(settlementId: String, deletedByMemberId: String): Result<Unit>
}

class OfflineFirstSettlementRepository(
    private val database: SettleZeroDatabase,
    private val timeProvider: TimeProvider,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SettlementRepository {

    private val settlementDao = database.settlementDao()
    private val expenseDao = database.expenseDao()
    private val memberDao = database.memberDao()
    private val groupDao = database.groupDao()
    private val syncQueueDao = database.syncQueueDao()
    private val historyDao = database.activityHistoryDao()

    override fun observeSettlements(groupId: String): Flow<List<Settlement>> =
        settlementDao.observeSettlements(groupId).map { list -> list.map { it.asDomain() } }

    override fun observeActiveSettlements(groupId: String): Flow<List<Settlement>> =
        settlementDao.observeActiveSettlements(groupId).map { list -> list.map { it.asDomain() } }

    override suspend fun getSettlements(groupId: String): List<Settlement> =
        settlementDao.getSettlements(groupId).map { it.asDomain() }

    override suspend fun getActiveSettlements(groupId: String): List<Settlement> =
        settlementDao.getActiveSettlements(groupId).map { it.asDomain() }

    override suspend fun getSettlement(settlementId: String): Settlement? =
        settlementDao.getSettlementById(settlementId)?.asDomain()

    override suspend fun recordSettlement(
        groupId: String,
        payerMemberId: String,
        recipientMemberId: String,
        amountMinor: Long,
        settlementDate: Long,
        recordedByMemberId: String,
        note: String?
    ): Result<Settlement> {
        val group = groupDao.getGroupById(groupId)
            ?: return Result.Error(IllegalArgumentException("Group does not exist: $groupId"))

        val members = memberDao.getMembers(groupId).associate { it.id to it.asDomain() }
        val expenses = expenseDao.getActiveExpenses(groupId).map { it.asDomain() }
        val settlements = settlementDao.getActiveSettlements(groupId).map { it.asDomain() }

        // Compute current outstanding debt between payer and recipient
        val netBalances = NetBalanceProjector.calculateNetBalances(members.keys, expenses, settlements)
        val simplifiedTransfers = DebtSimplifier.simplify(netBalances)
        val outstandingTransfer = simplifiedTransfers.find {
            it.fromMemberId == payerMemberId && it.toMemberId == recipientMemberId
        }

        // If not in simplified transfers, also check direct pairwise debts
        val directTransfers = DebtSimplifier.calculateDirectDebts(members.keys, expenses, settlements)
        val directTransfer = directTransfers.find {
            it.fromMemberId == payerMemberId && it.toMemberId == recipientMemberId
        }

        val maxDebt = maxOf(outstandingTransfer?.amountMinor ?: 0L, directTransfer?.amountMinor ?: 0L)

        val now = timeProvider.currentTimeMillis()
        val settlementId = UUID.randomUUID().toString()

        val settlement = Settlement(
            id = settlementId,
            groupId = groupId,
            payerMemberId = payerMemberId,
            recipientMemberId = recipientMemberId,
            amountMinor = amountMinor,
            settlementDate = settlementDate,
            createdAt = now,
            note = note?.trim()?.ifBlank { null }
        )

        val validation = TransactionValidator.validateSettlement(settlement, members, maxDebt)
        if (!validation.isValid) {
            val reason = (validation as ValidationResult.Invalid).reason
            return Result.Error(IllegalArgumentException(reason))
        }

        val settlementEntity = SettlementEntity(
            id = settlementId,
            groupId = groupId,
            payerMemberId = payerMemberId,
            recipientMemberId = recipientMemberId,
            amountMinor = amountMinor,
            settlementDate = settlementDate,
            createdAt = now,
            note = settlement.note
        )

        val payerName = members[payerMemberId]?.name ?: "Member"
        val recipientName = members[recipientMemberId]?.name ?: "Member"

        val historyEntity = ActivityHistoryEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            actorMemberId = recordedByMemberId,
            eventType = "SETTLEMENT_RECORDED",
            summary = "$payerName settled with $recipientName",
            entityId = settlementId,
            createdAt = now
        )

        val payload = RecordSettlementPayload(
            groupId = groupId,
            settlementId = settlementId,
            payerMemberId = payerMemberId,
            recipientMemberId = recipientMemberId,
            amountMinor = amountMinor,
            settlementDate = settlementDate,
            recordedByMemberId = recordedByMemberId,
            note = settlement.note,
            createdAt = now
        )

        val syncItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            operationType = SyncOperationTypes.RECORD_SETTLEMENT,
            payloadJson = json.encodeToString(payload),
            idempotencyKey = UUID.randomUUID().toString(),
            status = "PENDING",
            createdAt = now
        )

        return try {
            database.withTransaction {
                if (!group.isCurrencyLocked) {
                    groupDao.lockCurrency(groupId, now)
                }
                settlementDao.insertSettlement(settlementEntity)
                historyDao.insert(historyEntity)
                syncQueueDao.enqueue(syncItem)
            }
            Result.Success(settlement)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteSettlement(
        settlementId: String,
        deletedByMemberId: String
    ): Result<Unit> {
        val settlement = settlementDao.getSettlementById(settlementId)
            ?: return Result.Error(IllegalArgumentException("Settlement not found: $settlementId"))

        val members = memberDao.getMembers(settlement.groupId).associate { it.id to it.asDomain() }
        val actor = members[deletedByMemberId]
            ?: return Result.Error(IllegalArgumentException("Actor is not a member of this group: $deletedByMemberId"))

        val isParty = settlement.payerMemberId == deletedByMemberId || settlement.recipientMemberId == deletedByMemberId
        val isAdmin = actor.isAdmin

        if (!isParty && !isAdmin) {
            return Result.Error(IllegalAccessException("Only parties to the settlement or an admin can delete a settlement."))
        }

        val now = timeProvider.currentTimeMillis()
        val payload = DeleteSettlementPayload(
            groupId = settlement.groupId,
            settlementId = settlementId,
            deletedByMemberId = deletedByMemberId,
            deletedAt = now
        )

        val syncItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            groupId = settlement.groupId,
            operationType = SyncOperationTypes.DELETE_SETTLEMENT,
            payloadJson = json.encodeToString(payload),
            idempotencyKey = UUID.randomUUID().toString(),
            status = "PENDING",
            createdAt = now
        )

        return try {
            database.withTransaction {
                settlementDao.softDeleteSettlement(settlementId, deletedByMemberId, now)
                historyDao.insert(
                    ActivityHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        groupId = settlement.groupId,
                        actorMemberId = deletedByMemberId,
                        eventType = "SETTLEMENT_DELETED",
                        summary = "${actor.name} deleted settlement of ${settlement.amountMinor}",
                        entityId = settlementId,
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

