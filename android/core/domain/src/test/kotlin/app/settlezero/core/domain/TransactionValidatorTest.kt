package app.settlezero.core.domain

import app.settlezero.core.model.Expense
import app.settlezero.core.model.ExpenseSplitAllocation
import app.settlezero.core.model.Member
import app.settlezero.core.model.MemberRole
import app.settlezero.core.model.MemberStatus
import app.settlezero.core.model.Settlement
import app.settlezero.core.model.SplitType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransactionValidatorTest {

    private val activeMember1 = Member("m1", "g1", "Sayantan", MemberRole.SUPER_ADMIN, MemberStatus.ACTIVE_CLAIMED, 1000L)
    private val activeMember2 = Member("m2", "g1", "Rahul", MemberRole.MEMBER, MemberStatus.ACTIVE_UNCLAIMED, 1000L)
    private val archivedMember = Member("m3", "g1", "Priya", MemberRole.MEMBER, MemberStatus.ARCHIVED, 1000L)

    private val members = mapOf(
        "m1" to activeMember1,
        "m2" to activeMember2,
        "m3" to archivedMember
    )

    @Test
    fun `valid equal split expense passes validation`() {
        val expense = Expense(
            id = "e1",
            groupId = "g1",
            description = "Dinner",
            amountMinor = 2000L,
            payerMemberId = "m1",
            createdByMemberId = "m1",
            expenseDate = 1000L,
            createdAt = 1000L,
            allocations = listOf(
                ExpenseSplitAllocation("m1", 1000L),
                ExpenseSplitAllocation("m2", 1000L)
            )
        )

        val result = TransactionValidator.validateExpense(expense, members)
        assertThat(result.isValid).isTrue()
    }

    @Test
    fun `expense with blank description fails validation`() {
        val expense = Expense(
            id = "e1",
            groupId = "g1",
            description = "   ",
            amountMinor = 2000L,
            payerMemberId = "m1",
            createdByMemberId = "m1",
            expenseDate = 1000L,
            createdAt = 1000L,
            allocations = listOf(ExpenseSplitAllocation("m1", 2000L))
        )

        val result = TransactionValidator.validateExpense(expense, members)
        assertThat(result.isValid).isFalse()
        assertThat((result as ValidationResult.Invalid).reason).contains("description cannot be empty")
    }

    @Test
    fun `expense with archived payer fails validation`() {
        val expense = Expense(
            id = "e1",
            groupId = "g1",
            description = "Snacks",
            amountMinor = 2000L,
            payerMemberId = "m3", // Priya is archived
            createdByMemberId = "m1",
            expenseDate = 1000L,
            createdAt = 1000L,
            allocations = listOf(ExpenseSplitAllocation("m1", 2000L))
        )

        val result = TransactionValidator.validateExpense(expense, members)
        assertThat(result.isValid).isFalse()
        assertThat((result as ValidationResult.Invalid).reason).contains("Archived member 'Priya' cannot pay")
    }

    @Test
    fun `expense with archived participant fails validation`() {
        val expense = Expense(
            id = "e1",
            groupId = "g1",
            description = "Snacks",
            amountMinor = 2000L,
            payerMemberId = "m1",
            createdByMemberId = "m1",
            expenseDate = 1000L,
            createdAt = 1000L,
            allocations = listOf(
                ExpenseSplitAllocation("m1", 1000L),
                ExpenseSplitAllocation("m3", 1000L) // Priya is archived
            )
        )

        val result = TransactionValidator.validateExpense(expense, members)
        assertThat(result.isValid).isFalse()
        assertThat((result as ValidationResult.Invalid).reason).contains("Archived member 'Priya' cannot participate")
    }

    @Test
    fun `unequal split with sum mismatch fails validation`() {
        val expense = Expense(
            id = "e1",
            groupId = "g1",
            description = "Tickets",
            amountMinor = 5000L,
            payerMemberId = "m1",
            createdByMemberId = "m1",
            expenseDate = 1000L,
            createdAt = 1000L,
            splitType = SplitType.UNEQUAL,
            allocations = listOf(
                ExpenseSplitAllocation("m1", 2000L),
                ExpenseSplitAllocation("m2", 2000L) // Sum = 4000 != 5000
            )
        )

        val result = TransactionValidator.validateExpense(expense, members)
        assertThat(result.isValid).isFalse()
        assertThat((result as ValidationResult.Invalid).reason).contains("does not match total amount")
    }

    @Test
    fun `unequal split with zero allocation fails validation`() {
        val expense = Expense(
            id = "e1",
            groupId = "g1",
            description = "Tickets",
            amountMinor = 5000L,
            payerMemberId = "m1",
            createdByMemberId = "m1",
            expenseDate = 1000L,
            createdAt = 1000L,
            splitType = SplitType.UNEQUAL,
            allocations = listOf(
                ExpenseSplitAllocation("m1", 5000L),
                ExpenseSplitAllocation("m2", 0L)
            )
        )

        val result = TransactionValidator.validateExpense(expense, members)
        assertThat(result.isValid).isFalse()
        assertThat((result as ValidationResult.Invalid).reason).contains("Zero or negative allocations are not allowed")
    }

    @Test
    fun `valid settlement under outstanding debt passes validation`() {
        val settlement = Settlement(
            id = "s1",
            groupId = "g1",
            payerMemberId = "m2",
            recipientMemberId = "m1",
            amountMinor = 3000L,
            settlementDate = 2000L,
            createdAt = 2000L
        )

        // Rahul owes Sayantan ₹50, paying ₹30
        val result = TransactionValidator.validateSettlement(settlement, members, maxOutstandingDebt = 5000L)
        assertThat(result.isValid).isTrue()
    }

    @Test
    fun `overpayment settlement exceeding outstanding debt fails validation`() {
        val settlement = Settlement(
            id = "s1",
            groupId = "g1",
            payerMemberId = "m2",
            recipientMemberId = "m1",
            amountMinor = 7000L,
            settlementDate = 2000L,
            createdAt = 2000L
        )

        // Rahul owes Sayantan ₹50, attempting to pay ₹70
        val result = TransactionValidator.validateSettlement(settlement, members, maxOutstandingDebt = 5000L)
        assertThat(result.isValid).isFalse()
        assertThat((result as ValidationResult.Invalid).reason).contains("exceeds outstanding debt")
    }

    @Test
    fun `settlement when no debt exists fails validation`() {
        val settlement = Settlement(
            id = "s1",
            groupId = "g1",
            payerMemberId = "m2",
            recipientMemberId = "m1",
            amountMinor = 1000L,
            settlementDate = 2000L,
            createdAt = 2000L
        )

        val result = TransactionValidator.validateSettlement(settlement, members, maxOutstandingDebt = 0L)
        assertThat(result.isValid).isFalse()
        assertThat((result as ValidationResult.Invalid).reason).contains("does not currently owe")
    }
}

