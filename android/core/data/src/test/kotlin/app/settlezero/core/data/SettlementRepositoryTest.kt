package app.settlezero.core.data

import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.OfflineFirstExpenseRepository
import app.settlezero.core.data.repository.OfflineFirstGroupRepository
import app.settlezero.core.data.repository.OfflineFirstMemberRepository
import app.settlezero.core.data.repository.OfflineFirstSettlementRepository
import app.settlezero.core.model.Currency
import app.settlezero.core.model.ExpenseSplitAllocation
import app.settlezero.core.model.Member
import app.settlezero.core.model.SplitType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SettlementRepositoryTest : BaseRepositoryTest() {

    private lateinit var groupRepo: OfflineFirstGroupRepository
    private lateinit var memberRepo: OfflineFirstMemberRepository
    private lateinit var expenseRepo: OfflineFirstExpenseRepository
    private lateinit var settlementRepo: OfflineFirstSettlementRepository

    private lateinit var groupId: String
    private lateinit var userA: Member
    private lateinit var userB: Member

    @Before
    override fun setUp() {
        super.setUp()
        groupRepo = OfflineFirstGroupRepository(database, timeProvider)
        memberRepo = OfflineFirstMemberRepository(database, timeProvider)
        expenseRepo = OfflineFirstExpenseRepository(database, timeProvider)
        settlementRepo = OfflineFirstSettlementRepository(database, timeProvider)

        runTest {
            val (group, creator) = (groupRepo.createGroup("Trip", Currency.INR, "Sayantan") as Result.Success).data
            groupId = group.id
            userA = creator
            userB = (memberRepo.addMember(groupId, "Rahul", userA.id) as Result.Success).data

            // Create expense: userA pays 2000 for userA and userB (userB owes userA 1000)
            expenseRepo.createExpense(
                groupId = groupId,
                description = "Lunch",
                amountMinor = 2000L,
                payerMemberId = userA.id,
                createdByMemberId = userA.id,
                expenseDate = 1000L,
                splitType = SplitType.EQUAL,
                allocations = listOf(
                    ExpenseSplitAllocation(userA.id, 1000L),
                    ExpenseSplitAllocation(userB.id, 1000L)
                )
            )
        }
    }

    @Test
    fun `record valid settlement commits locally and enqueues sync`() = runTest {
        // userB settles 500 out of 1000 debt to userA
        val result = settlementRepo.recordSettlement(
            groupId = groupId,
            payerMemberId = userB.id,
            recipientMemberId = userA.id,
            amountMinor = 500L,
            settlementDate = 2000L,
            recordedByMemberId = userB.id
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val settlement = (result as Result.Success).data
        assertThat(settlement.amountMinor).isEqualTo(500L)

        val active = settlementRepo.observeActiveSettlements(groupId).first()
        assertThat(active).hasSize(1)
        assertThat(active.first().id).isEqualTo(settlement.id)

        val pending = database.syncQueueDao().getPendingOperations()
        assertThat(pending.any { it.operationType == "RECORD_SETTLEMENT" }).isTrue()
    }

    @Test
    fun `settlement exceeding debt is rejected as overpayment`() = runTest {
        // userB owes 1000, attempts to pay 1500
        val result = settlementRepo.recordSettlement(
            groupId = groupId,
            payerMemberId = userB.id,
            recipientMemberId = userA.id,
            amountMinor = 1500L,
            settlementDate = 2000L,
            recordedByMemberId = userB.id
        )

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat((result as Result.Error).message).contains("exceeds outstanding debt")
    }

    @Test
    fun `deleteSettlement soft deletes settlement`() = runTest {
        val settlement = (settlementRepo.recordSettlement(
            groupId = groupId,
            payerMemberId = userB.id,
            recipientMemberId = userA.id,
            amountMinor = 500L,
            settlementDate = 2000L,
            recordedByMemberId = userB.id
        ) as Result.Success).data

        val deleteResult = settlementRepo.deleteSettlement(settlement.id, deletedByMemberId = userB.id)
        assertThat(deleteResult).isInstanceOf(Result.Success::class.java)

        val active = settlementRepo.observeActiveSettlements(groupId).first()
        val all = settlementRepo.observeSettlements(groupId).first()

        assertThat(active).isEmpty()
        assertThat(all).hasSize(1)
        assertThat(all.first().isDeleted).isTrue()
    }
}

