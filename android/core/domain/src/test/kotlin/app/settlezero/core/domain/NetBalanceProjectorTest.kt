package app.settlezero.core.domain

import app.settlezero.core.model.Expense
import app.settlezero.core.model.ExpenseSplitAllocation
import app.settlezero.core.model.Settlement
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetBalanceProjectorTest {

    private val memberA = "member-a"
    private val memberB = "member-b"
    private val memberC = "member-c"
    private val allMembers = listOf(memberA, memberB, memberC)

    @Test
    fun `empty transaction ledger results in all zero balances`() {
        val balances = NetBalanceProjector.calculateNetBalances(allMembers, emptyList(), emptyList())
        assertThat(balances[memberA]).isEqualTo(0L)
        assertThat(balances[memberB]).isEqualTo(0L)
        assertThat(balances[memberC]).isEqualTo(0L)
    }

    @Test
    fun `single expense where payer participates produces correct net balances`() {
        // A pays ₹300, split equally between A, B, C (₹100 each)
        // A: +300 (paid) - 100 (share) = +200
        // B: -100
        // C: -100
        val expense = Expense(
            id = "exp-1",
            groupId = "grp-1",
            description = "Dinner",
            amountMinor = 30000L,
            payerMemberId = memberA,
            createdByMemberId = memberA,
            expenseDate = 1000L,
            createdAt = 1000L,
            allocations = listOf(
                ExpenseSplitAllocation(memberA, 10000L),
                ExpenseSplitAllocation(memberB, 10000L),
                ExpenseSplitAllocation(memberC, 10000L)
            )
        )

        val balances = NetBalanceProjector.calculateNetBalances(allMembers, listOf(expense), emptyList())

        assertThat(balances[memberA]).isEqualTo(20000L)
        assertThat(balances[memberB]).isEqualTo(-10000L)
        assertThat(balances[memberC]).isEqualTo(-10000L)
        assertThat(balances.values.sum()).isEqualTo(0L)
    }

    @Test
    fun `payer who does not participate is credited full amount`() {
        // A pays ₹100 for B and C (₹50 each)
        // A: +100
        // B: -50
        // C: -50
        val expense = Expense(
            id = "exp-2",
            groupId = "grp-1",
            description = "Taxi for friends",
            amountMinor = 10000L,
            payerMemberId = memberA,
            createdByMemberId = memberA,
            expenseDate = 1000L,
            createdAt = 1000L,
            allocations = listOf(
                ExpenseSplitAllocation(memberB, 5000L),
                ExpenseSplitAllocation(memberC, 5000L)
            )
        )

        val balances = NetBalanceProjector.calculateNetBalances(allMembers, listOf(expense), emptyList())

        assertThat(balances[memberA]).isEqualTo(10000L)
        assertThat(balances[memberB]).isEqualTo(-5000L)
        assertThat(balances[memberC]).isEqualTo(-5000L)
        assertThat(balances.values.sum()).isEqualTo(0L)
    }

    @Test
    fun `settlement reduces balance between debtor and creditor`() {
        // A pays ₹300 for A, B, C (net: A +200, B -100, C -100)
        // B settles ₹100 to A (B pays A)
        // Expected: A +100, B 0, C -100
        val expense = Expense(
            id = "exp-1",
            groupId = "grp-1",
            description = "Dinner",
            amountMinor = 30000L,
            payerMemberId = memberA,
            createdByMemberId = memberA,
            expenseDate = 1000L,
            createdAt = 1000L,
            allocations = listOf(
                ExpenseSplitAllocation(memberA, 10000L),
                ExpenseSplitAllocation(memberB, 10000L),
                ExpenseSplitAllocation(memberC, 10000L)
            )
        )

        val settlement = Settlement(
            id = "set-1",
            groupId = "grp-1",
            payerMemberId = memberB,
            recipientMemberId = memberA,
            amountMinor = 10000L,
            settlementDate = 2000L,
            createdAt = 2000L
        )

        val balances = NetBalanceProjector.calculateNetBalances(
            allMembers,
            listOf(expense),
            listOf(settlement)
        )

        assertThat(balances[memberA]).isEqualTo(10000L)
        assertThat(balances[memberB]).isEqualTo(0L)
        assertThat(balances[memberC]).isEqualTo(-10000L)
        assertThat(balances.values.sum()).isEqualTo(0L)
    }

    @Test
    fun `soft deleted expenses and settlements are completely excluded`() {
        val deletedExpense = Expense(
            id = "exp-del",
            groupId = "grp-1",
            description = "Mistake",
            amountMinor = 50000L,
            payerMemberId = memberA,
            createdByMemberId = memberA,
            expenseDate = 1000L,
            createdAt = 1000L,
            isDeleted = true,
            allocations = listOf(ExpenseSplitAllocation(memberB, 50000L))
        )

        val balances = NetBalanceProjector.calculateNetBalances(
            allMembers,
            listOf(deletedExpense),
            emptyList()
        )

        assertThat(balances[memberA]).isEqualTo(0L)
        assertThat(balances[memberB]).isEqualTo(0L)
    }

    @Test
    fun `total spending excludes soft-deleted items and ignores settlements`() {
        val activeExp = Expense(
            id = "exp-act",
            groupId = "grp-1",
            description = "Groceries",
            amountMinor = 1500L,
            payerMemberId = memberA,
            createdByMemberId = memberA,
            expenseDate = 1000L,
            createdAt = 1000L,
            allocations = listOf(ExpenseSplitAllocation(memberA, 1500L))
        )
        val deletedExp = Expense(
            id = "exp-del",
            groupId = "grp-1",
            description = "Cancelled",
            amountMinor = 3500L,
            payerMemberId = memberA,
            createdByMemberId = memberA,
            expenseDate = 1000L,
            createdAt = 1000L,
            isDeleted = true,
            allocations = listOf(ExpenseSplitAllocation(memberA, 3500L))
        )

        val total = NetBalanceProjector.calculateTotalSpending(listOf(activeExp, deletedExp))
        assertThat(total).isEqualTo(1500L)
    }
}

