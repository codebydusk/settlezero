package app.settlezero.core.data

import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.OfflineFirstExpenseRepository
import app.settlezero.core.data.repository.OfflineFirstGroupRepository
import app.settlezero.core.data.repository.OfflineFirstLedgerRepository
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

class LedgerRepositoryTest : BaseRepositoryTest() {

    private lateinit var groupRepo: OfflineFirstGroupRepository
    private lateinit var memberRepo: OfflineFirstMemberRepository
    private lateinit var expenseRepo: OfflineFirstExpenseRepository
    private lateinit var settlementRepo: OfflineFirstSettlementRepository
    private lateinit var ledgerRepo: OfflineFirstLedgerRepository

    private lateinit var groupId: String
    private lateinit var userA: Member
    private lateinit var userB: Member
    private lateinit var userC: Member

    @Before
    override fun setUp() {
        super.setUp()
        groupRepo = OfflineFirstGroupRepository(database, timeProvider)
        memberRepo = OfflineFirstMemberRepository(database, timeProvider)
        expenseRepo = OfflineFirstExpenseRepository(database, timeProvider)
        settlementRepo = OfflineFirstSettlementRepository(database, timeProvider)
        ledgerRepo = OfflineFirstLedgerRepository(database)

        runTest {
            val (group, creator) = (groupRepo.createGroup("Goa", Currency.INR, "Sayantan") as Result.Success).data
            groupId = group.id
            userA = creator
            userB = (memberRepo.addMember(groupId, "Rahul", userA.id) as Result.Success).data
            userC = (memberRepo.addMember(groupId, "Priya", userA.id) as Result.Success).data
        }
    }

    @Test
    fun `ledger reacts to expenses and settlements correctly`() = runTest {
        // Initial state
        var balances = ledgerRepo.observeGroupBalances(groupId).first()
        assertThat(balances[userA.id]).isEqualTo(0L)
        assertThat(balances[userB.id]).isEqualTo(0L)
        assertThat(balances[userC.id]).isEqualTo(0L)

        // Expense 1: userA pays 3000 for A, B, C (1000 each)
        expenseRepo.createExpense(
            groupId = groupId,
            description = "Dinner",
            amountMinor = 3000L,
            payerMemberId = userA.id,
            createdByMemberId = userA.id,
            expenseDate = 1000L,
            splitType = SplitType.EQUAL,
            allocations = listOf(
                ExpenseSplitAllocation(userA.id, 1000L),
                ExpenseSplitAllocation(userB.id, 1000L),
                ExpenseSplitAllocation(userC.id, 1000L)
            )
        )

        balances = ledgerRepo.observeGroupBalances(groupId).first()
        assertThat(balances[userA.id]).isEqualTo(2000L)
        assertThat(balances[userB.id]).isEqualTo(-1000L)
        assertThat(balances[userC.id]).isEqualTo(-1000L)

        val totalSpending = ledgerRepo.observeTotalSpending(groupId).first()
        assertThat(totalSpending).isEqualTo(3000L)

        val simplified = ledgerRepo.observeSimplifiedDebts(groupId).first()
        assertThat(simplified).hasSize(2)

        // Settlement: userB settles 1000 to userA
        settlementRepo.recordSettlement(
            groupId = groupId,
            payerMemberId = userB.id,
            recipientMemberId = userA.id,
            amountMinor = 1000L,
            settlementDate = 2000L,
            recordedByMemberId = userB.id
        )

        balances = ledgerRepo.observeGroupBalances(groupId).first()
        assertThat(balances[userA.id]).isEqualTo(1000L)
        assertThat(balances[userB.id]).isEqualTo(0L)
        assertThat(balances[userC.id]).isEqualTo(-1000L)

        val updatedSimplified = ledgerRepo.observeSimplifiedDebts(groupId).first()
        assertThat(updatedSimplified).hasSize(1)
        assertThat(updatedSimplified.first().fromMemberId).isEqualTo(userC.id)
        assertThat(updatedSimplified.first().toMemberId).isEqualTo(userA.id)
        assertThat(updatedSimplified.first().amountMinor).isEqualTo(1000L)
    }
}

