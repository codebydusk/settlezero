package app.settlezero.core.database

import app.settlezero.core.database.model.GroupEntity
import app.settlezero.core.database.model.MemberEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GroupDaoTest : BaseDatabaseTest() {

    private val sampleGroup = GroupEntity(
        id = "grp-1",
        name = "Goa Trip 2026",
        currencyCode = "INR",
        currencyScale = 2,
        status = "ACTIVE",
        inviteToken = "invite-token-abc",
        isCurrencyLocked = false,
        isReadOnly = false,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun `insert and retrieve group by id and invite token`() = runTest {
        database.groupDao().insertGroup(sampleGroup)

        val retrievedById = database.groupDao().getGroupById("grp-1")
        assertThat(retrievedById).isNotNull()
        assertThat(retrievedById?.name).isEqualTo("Goa Trip 2026")
        assertThat(retrievedById?.currencyCode).isEqualTo("INR")

        val retrievedByToken = database.groupDao().getGroupByInviteToken("invite-token-abc")
        assertThat(retrievedByToken).isEqualTo(sampleGroup)
    }

    @Test
    fun `observe active and archived groups filters correctly`() = runTest {
        val archivedGroup = sampleGroup.copy(id = "grp-2", inviteToken = "token-2", status = "ARCHIVED")

        database.groupDao().insertGroup(sampleGroup)
        database.groupDao().insertGroup(archivedGroup)

        val active = database.groupDao().observeActiveGroups().first()
        val archived = database.groupDao().observeArchivedGroups().first()

        assertThat(active).hasSize(1)
        assertThat(active.first().id).isEqualTo("grp-1")

        assertThat(archived).hasSize(1)
        assertThat(archived.first().id).isEqualTo("grp-2")
    }

    @Test
    fun `lockCurrency updates is_currency_locked to true`() = runTest {
        database.groupDao().insertGroup(sampleGroup)
        assertThat(database.groupDao().getGroupById("grp-1")?.isCurrencyLocked).isFalse()

        database.groupDao().lockCurrency("grp-1", 2000L)

        val updated = database.groupDao().getGroupById("grp-1")
        assertThat(updated?.isCurrencyLocked).isTrue()
        assertThat(updated?.updatedAt).isEqualTo(2000L)
    }

    @Test
    fun `deleting group cascades to members`() = runTest {
        database.groupDao().insertGroup(sampleGroup)
        val member = MemberEntity(
            id = "mem-1",
            groupId = "grp-1",
            name = "Sayantan",
            role = "SUPER_ADMIN",
            status = "ACTIVE_CLAIMED",
            createdAt = 1000L
        )
        database.memberDao().insertMember(member)

        assertThat(database.memberDao().getMembers("grp-1")).hasSize(1)

        database.groupDao().deleteGroup("grp-1")

        assertThat(database.groupDao().getGroupById("grp-1")).isNull()
        assertThat(database.memberDao().getMembers("grp-1")).isEmpty()
    }
}

