package app.settlezero.core.data.repository

import androidx.room.withTransaction
import app.settlezero.core.common.Result
import app.settlezero.core.common.TimeProvider
import app.settlezero.core.data.sync.CreateGroupPayload
import app.settlezero.core.data.sync.SyncOperationTypes
import app.settlezero.core.database.SettleZeroDatabase
import app.settlezero.core.database.model.ActivityHistoryEntity
import app.settlezero.core.database.model.GroupEntity
import app.settlezero.core.database.model.MemberEntity
import app.settlezero.core.database.model.SyncQueueEntity
import app.settlezero.core.database.model.asDomain
import app.settlezero.core.model.Currency
import app.settlezero.core.model.Group
import app.settlezero.core.model.Member
import app.settlezero.core.model.MemberRole
import app.settlezero.core.model.MemberStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

interface GroupRepository {
    fun observeActiveGroups(): Flow<List<Group>>
    fun observeArchivedGroups(): Flow<List<Group>>
    fun observeGroup(groupId: String): Flow<Group?>
    suspend fun getGroup(groupId: String): Group?
    suspend fun getGroupByInviteToken(token: String): Group?
    suspend fun createGroup(
        name: String,
        currency: Currency,
        creatorName: String
    ): Result<Pair<Group, Member>>
    suspend fun archiveGroup(groupId: String, actorMemberId: String): Result<Unit>
    suspend fun restoreGroup(groupId: String, actorMemberId: String): Result<Unit>
}

class OfflineFirstGroupRepository(
    private val database: SettleZeroDatabase,
    private val timeProvider: TimeProvider,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : GroupRepository {

    private val groupDao = database.groupDao()
    private val memberDao = database.memberDao()
    private val syncQueueDao = database.syncQueueDao()
    private val historyDao = database.activityHistoryDao()

    override fun observeActiveGroups(): Flow<List<Group>> =
        groupDao.observeActiveGroups().map { list -> list.map { it.asDomain() } }

    override fun observeArchivedGroups(): Flow<List<Group>> =
        groupDao.observeArchivedGroups().map { list -> list.map { it.asDomain() } }

    override fun observeGroup(groupId: String): Flow<Group?> =
        groupDao.observeGroupById(groupId).map { it?.asDomain() }

    override suspend fun getGroup(groupId: String): Group? =
        groupDao.getGroupById(groupId)?.asDomain()

    override suspend fun getGroupByInviteToken(token: String): Group? =
        groupDao.getGroupByInviteToken(token)?.asDomain()

    override suspend fun createGroup(
        name: String,
        currency: Currency,
        creatorName: String
    ): Result<Pair<Group, Member>> {
        if (name.isBlank()) {
            return Result.Error(IllegalArgumentException("Group name cannot be blank"))
        }
        if (creatorName.isBlank()) {
            return Result.Error(IllegalArgumentException("Creator name cannot be blank"))
        }

        val now = timeProvider.currentTimeMillis()
        val groupId = UUID.randomUUID().toString()
        val creatorMemberId = UUID.randomUUID().toString()
        val inviteToken = UUID.randomUUID().toString().replace("-", "")

        val groupEntity = GroupEntity(
            id = groupId,
            name = name.trim(),
            currencyCode = currency.code,
            currencyScale = currency.scale,
            status = "ACTIVE",
            inviteToken = inviteToken,
            isCurrencyLocked = false,
            isReadOnly = false,
            createdAt = now,
            updatedAt = now
        )

        val creatorEntity = MemberEntity(
            id = creatorMemberId,
            groupId = groupId,
            name = creatorName.trim(),
            role = MemberRole.SUPER_ADMIN.name,
            status = MemberStatus.ACTIVE_CLAIMED.name,
            createdAt = now
        )

        val historyEntity = ActivityHistoryEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            actorMemberId = creatorMemberId,
            eventType = "GROUP_CREATED",
            summary = "$creatorName created group '$name'",
            entityId = groupId,
            createdAt = now
        )

        val payload = CreateGroupPayload(
            groupId = groupId,
            name = name.trim(),
            currencyCode = currency.code,
            currencyScale = currency.scale,
            creatorMemberId = creatorMemberId,
            creatorName = creatorName.trim(),
            inviteToken = inviteToken,
            createdAt = now
        )

        val syncItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            operationType = SyncOperationTypes.CREATE_GROUP,
            payloadJson = json.encodeToString(payload),
            idempotencyKey = UUID.randomUUID().toString(),
            status = "PENDING",
            createdAt = now
        )

        return try {
            database.withTransaction {
                groupDao.insertGroup(groupEntity)
                memberDao.insertMember(creatorEntity)
                historyDao.insert(historyEntity)
                syncQueueDao.enqueue(syncItem)
            }
            Result.Success(groupEntity.asDomain() to creatorEntity.asDomain())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun archiveGroup(groupId: String, actorMemberId: String): Result<Unit> {
        val now = timeProvider.currentTimeMillis()
        return try {
            database.withTransaction {
                groupDao.updateStatus(groupId, "ARCHIVED", now)
                historyDao.insert(
                    ActivityHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        groupId = groupId,
                        actorMemberId = actorMemberId,
                        eventType = "GROUP_ARCHIVED",
                        summary = "Group was archived",
                        entityId = groupId,
                        createdAt = now
                    )
                )
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun restoreGroup(groupId: String, actorMemberId: String): Result<Unit> {
        val now = timeProvider.currentTimeMillis()
        return try {
            database.withTransaction {
                groupDao.updateStatus(groupId, "ACTIVE", now)
                historyDao.insert(
                    ActivityHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        groupId = groupId,
                        actorMemberId = actorMemberId,
                        eventType = "GROUP_RESTORED",
                        summary = "Group was restored",
                        entityId = groupId,
                        createdAt = now
                    )
                )
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

