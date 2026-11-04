package app.settlezero.core.data

import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.OfflineFirstGroupRepository
import app.settlezero.core.data.repository.OfflineFirstMemberRepository
import app.settlezero.core.model.Currency
import app.settlezero.core.model.Member
import app.settlezero.core.model.MemberRole
import app.settlezero.core.model.MemberStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MemberRepositoryTest : BaseRepositoryTest() {

    private lateinit var groupRepo: OfflineFirstGroupRepository
    private lateinit var memberRepo: OfflineFirstMemberRepository
    private lateinit var groupId: String
    private lateinit var admin: Member

    @Before
    override fun setUp() {
        super.setUp()
        groupRepo = OfflineFirstGroupRepository(database, timeProvider)
        memberRepo = OfflineFirstMemberRepository(database, timeProvider)

        runTest {
            val (group, creator) = (groupRepo.createGroup("Trip", Currency.INR, "Sayantan") as Result.Success).data
            groupId = group.id
            admin = creator
        }
    }

    @Test
    fun `addMember inserts member as active unclaimed and enqueues sync`() = runTest {
        val result = memberRepo.addMember(groupId, "Rahul", admin.id)
        assertThat(result).isInstanceOf(Result.Success::class.java)

        val member = (result as Result.Success).data
        assertThat(member.name).isEqualTo("Rahul")
        assertThat(member.status).isEqualTo(MemberStatus.ACTIVE_UNCLAIMED)
        assertThat(member.role).isEqualTo(MemberRole.MEMBER)

        val members = memberRepo.observeMembers(groupId).first()
        assertThat(members).hasSize(2)

        val pending = database.syncQueueDao().getPendingOperations()
        assertThat(pending.any { it.operationType == "ADD_MEMBER" }).isTrue()
    }

    @Test
    fun `adding duplicate member name fails with error`() = runTest {
        memberRepo.addMember(groupId, "Rahul", admin.id)

        // Duplicate with different case
        val duplicateResult = memberRepo.addMember(groupId, "rahul", admin.id)
        assertThat(duplicateResult).isInstanceOf(Result.Error::class.java)
        assertThat((duplicateResult as Result.Error).message).contains("already exists")
    }

    @Test
    fun `archiveMember transitions status to ARCHIVED`() = runTest {
        val member = (memberRepo.addMember(groupId, "Rahul", admin.id) as Result.Success).data

        val archiveResult = memberRepo.archiveMember(member.id, admin.id)
        assertThat(archiveResult).isInstanceOf(Result.Success::class.java)

        val updated = memberRepo.getMember(member.id)
        assertThat(updated?.status).isEqualTo(MemberStatus.ARCHIVED)
        assertThat(updated?.isArchived).isTrue()
    }
}

