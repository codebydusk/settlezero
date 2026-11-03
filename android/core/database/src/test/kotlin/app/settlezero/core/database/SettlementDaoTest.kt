package app.settlezero.core.database

import app.settlezero.core.database.model.GroupEntity
import app.settlezero.core.database.model.MemberEntity
import app.settlezero.core.database.model.SettlementEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SettlementDaoTest : BaseDatabaseTest() {

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

    private val memberA = MemberEntity("mA", "grp-1", "Sayantan", "SUPER_ADMIN", "ACTIVE_CLAIMED", 1000L)
    private val memberB = MemberEntity("mB", "grp-1", "Rahul", "MEMBER", "ACTIVE_CLAIMED", 1000L)

    @Before
    override fun setUp() {
        super.setUp()
        runTest {
            database.groupDao().insertGroup(group)
            database.memberDao().insertMembers(listOf(memberA, memberB))
        }
    }

    @Test
    fun `insert and retrieve settlement`() = runTest {
        val settlement = SettlementEntity(
            id = "set-1",
            groupId = "grp-1",
            payerMemberId = "mB",
            recipientMemberId = "mA",
            amountMinor = 1500L,
            settlementDate = 2000L,
            createdAt = 2000L,
            note = "UPI"
        )
        database.settlementDao().insertSettlement(settlement)

        val retrieved = database.settlementDao().getSettlementById("set-1")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.amountMinor).isEqualTo(1500L)
        assertThat(retrieved?.note).isEqualTo("UPI")
    }

    @Test
    fun `soft delete excludes settlement from active query`() = runTest {
        val s1 = SettlementEntity("s1", "grp-1", "mB", "mA", 1000L, 1000L, 1000L)
        val s2 = SettlementEntity("s2", "grp-1", "mB", "mA", 2000L, 2000L, 2000L)

        database.settlementDao().insertSettlement(s1)
        database.settlementDao().insertSettlement(s2)

        database.settlementDao().softDeleteSettlement("s1", deletedByMemberId = "mA", deletedAt = 2500L)

        val active = database.settlementDao().observeActiveSettlements("grp-1").first()
        val all = database.settlementDao().observeSettlements("grp-1").first()

        assertThat(active).hasSize(1)
        assertThat(active.first().id).isEqualTo("s2")

        assertThat(all).hasSize(2)

        val deleted = database.settlementDao().getSettlementById("s1")
        assertThat(deleted?.isDeleted).isTrue()
        assertThat(deleted?.deletedByMemberId).isEqualTo("mA")
        assertThat(deleted?.deletedAt).isEqualTo(2500L)
    }
}

