package app.settlezero.core.database

import android.database.sqlite.SQLiteConstraintException
import app.settlezero.core.database.model.GroupEntity
import app.settlezero.core.database.model.MemberEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MemberDaoTest : BaseDatabaseTest() {

    private val group = GroupEntity(
        id = "grp-1",
        name = "Trip",
        currencyCode = "INR",
        currencyScale = 2,
        status = "ACTIVE",
        inviteToken = "token",
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Before
    override fun setUp() {
        super.setUp()
        runTest {
            database.groupDao().insertGroup(group)
        }
    }

    @Test
    fun `insert members and retrieve sorted case-insensitively by name`() = runTest {
        val m1 = MemberEntity("m1", "grp-1", "sayantan", "SUPER_ADMIN", "ACTIVE_CLAIMED", 1000L)
        val m2 = MemberEntity("m2", "grp-1", "Amit", "MEMBER", "ACTIVE_UNCLAIMED", 1000L)
        val m3 = MemberEntity("m3", "grp-1", "Rahul", "ADMIN", "ACTIVE_CLAIMED", 1000L)

        database.memberDao().insertMembers(listOf(m1, m2, m3))

        val members = database.memberDao().observeMembers("grp-1").first()

        // NOCASE order: Amit, Rahul, sayantan
        assertThat(members.map { it.name }).containsExactly("Amit", "Rahul", "sayantan").inOrder()
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `inserting duplicate member name within same group throws constraint exception`() = runTest {
        val m1 = MemberEntity("m1", "grp-1", "Rahul", "MEMBER", "ACTIVE_CLAIMED", 1000L)
        val m2 = MemberEntity("m2", "grp-1", "Rahul", "MEMBER", "ACTIVE_UNCLAIMED", 1000L)

        database.memberDao().insertMember(m1)
        database.memberDao().insertMember(m2)
    }

    @Test
    fun `update member status and role updates correctly`() = runTest {
        val m1 = MemberEntity("m1", "grp-1", "Rahul", "MEMBER", "ACTIVE_UNCLAIMED", 1000L)
        database.memberDao().insertMember(m1)

        database.memberDao().updateStatus("m1", "ACTIVE_CLAIMED")
        database.memberDao().updateRole("m1", "ADMIN")

        val updated = database.memberDao().getMemberById("m1")
        assertThat(updated?.status).isEqualTo("ACTIVE_CLAIMED")
        assertThat(updated?.role).isEqualTo("ADMIN")
    }
}

