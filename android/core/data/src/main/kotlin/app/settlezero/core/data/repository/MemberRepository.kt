package app.settlezero.core.data.repository

import androidx.room.withTransaction
import app.settlezero.core.common.Result
import app.settlezero.core.common.TimeProvider
import app.settlezero.core.data.sync.AddMemberPayload
import app.settlezero.core.data.sync.SyncOperationTypes
import app.settlezero.core.database.SettleZeroDatabase
import app.settlezero.core.database.model.ActivityHistoryEntity
import app.settlezero.core.database.model.MemberEntity
import app.settlezero.core.database.model.SyncQueueEntity
import app.settlezero.core.database.model.asDomain
import app.settlezero.core.model.Member
import app.settlezero.core.model.MemberRole
import app.settlezero.core.model.MemberStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

interface MemberRepository {
    fun observeMembers(groupId: String): Flow<List<Member>>
    suspend fun getMembers(groupId: String): List<Member>
    suspend fun getMember(memberId: String): Member?
    suspend fun addMember(groupId: String, name: String, actorMemberId: String): Result<Member>
    suspend fun archiveMember(memberId: String, actorMemberId: String): Result<Unit>
    suspend fun updateMemberRole(memberId: String, newRole: MemberRole, actorMemberId: String): Result<Unit>
}

class OfflineFirstMemberRepository(
    private val database: SettleZeroDatabase,
    private val timeProvider: TimeProvider,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : MemberRepository {

    private val memberDao = database.memberDao()
    private val groupDao = database.groupDao()
    private val syncQueueDao = database.syncQueueDao()
    private val historyDao = database.activityHistoryDao()

    override fun observeMembers(groupId: String): Flow<List<Member>> =
        memberDao.observeMembers(groupId).map { list -> list.map { it.asDomain() } }

    override suspend fun getMembers(groupId: String): List<Member> =
        memberDao.getMembers(groupId).map { it.asDomain() }

    override suspend fun getMember(memberId: String): Member? =
        memberDao.getMemberById(memberId)?.asDomain()

    override suspend fun addMember(
        groupId: String,
        name: String,
        actorMemberId: String
    ): Result<Member> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.Error(IllegalArgumentException("Member name cannot be blank"))
        }

        val existing = memberDao.getMemberByName(groupId, trimmedName)
        if (existing != null) {
            return Result.Error(IllegalArgumentException("A member named '$trimmedName' already exists in this group."))
        }

        val group = groupDao.getGroupById(groupId)
            ?: return Result.Error(IllegalArgumentException("Group does not exist: $groupId"))

        if (group.status == "ARCHIVED") {
            return Result.Error(IllegalStateException("Cannot add members to an archived group."))
        }

        val now = timeProvider.currentTimeMillis()
        val memberId = UUID.randomUUID().toString()

        val memberEntity = MemberEntity(
            id = memberId,
            groupId = groupId,
            name = trimmedName,
            role = MemberRole.MEMBER.name,
            status = MemberStatus.ACTIVE_UNCLAIMED.name,
            createdAt = now
        )

        val historyEntity = ActivityHistoryEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            actorMemberId = actorMemberId,
            eventType = "MEMBER_ADDED",
            summary = "Added member '$trimmedName'",
            entityId = memberId,
            createdAt = now
        )

        val payload = AddMemberPayload(
            groupId = groupId,
            memberId = memberId,
            name = trimmedName,
            role = MemberRole.MEMBER.name,
            status = MemberStatus.ACTIVE_UNCLAIMED.name,
            addedByMemberId = actorMemberId,
            createdAt = now
        )

        val syncItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            operationType = SyncOperationTypes.ADD_MEMBER,
            payloadJson = json.encodeToString(payload),
            idempotencyKey = UUID.randomUUID().toString(),
            status = "PENDING",
            createdAt = now
        )

        return try {
            database.withTransaction {
                memberDao.insertMember(memberEntity)
                historyDao.insert(historyEntity)
                syncQueueDao.enqueue(syncItem)
            }
            Result.Success(memberEntity.asDomain())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun archiveMember(memberId: String, actorMemberId: String): Result<Unit> {
        val member = memberDao.getMemberById(memberId)
            ?: return Result.Error(IllegalArgumentException("Member not found: $memberId"))

        val now = timeProvider.currentTimeMillis()
        return try {
            database.withTransaction {
                memberDao.updateStatus(memberId, MemberStatus.ARCHIVED.name)
                historyDao.insert(
                    ActivityHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        groupId = member.groupId,
                        actorMemberId = actorMemberId,
                        eventType = "MEMBER_ARCHIVED",
                        summary = "Archived member '${member.name}'",
                        entityId = memberId,
                        createdAt = now
                    )
                )
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateMemberRole(
        memberId: String,
        newRole: MemberRole,
        actorMemberId: String
    ): Result<Unit> {
        val member = memberDao.getMemberById(memberId)
            ?: return Result.Error(IllegalArgumentException("Member not found: $memberId"))

        val now = timeProvider.currentTimeMillis()
        return try {
            database.withTransaction {
                memberDao.updateRole(memberId, newRole.name)
                historyDao.insert(
                    ActivityHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        groupId = member.groupId,
                        actorMemberId = actorMemberId,
                        eventType = "MEMBER_ROLE_UPDATED",
                        summary = "Updated role of '${member.name}' to ${newRole.name}",
                        entityId = memberId,
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

