package app.settlezero.core.domain

import app.settlezero.core.model.Expense
import app.settlezero.core.model.Settlement

/**
 * Calculates net balances for all members from the immutable transaction ledger.
 *
 * Invariant: Sum of all net balances strictly equals zero.
 * Soft-deleted expenses and settlements are completely excluded.
 */
object NetBalanceProjector {

    fun calculateNetBalances(
        memberIds: Collection<String>,
        expenses: Collection<Expense>,
        settlements: Collection<Settlement>
    ): Map<String, Long> {
        val balances = mutableMapOf<String, Long>()
        for (memberId in memberIds) {
            balances[memberId] = 0L
        }

        for (expense in expenses) {
            if (expense.isDeleted) continue

            // Payer is credited with the total fronted amount
            balances[expense.payerMemberId] = (balances[expense.payerMemberId] ?: 0L) + expense.amountMinor

            // Each participant is debited their allocated share
            for (allocation in expense.allocations) {
                balances[allocation.memberId] = (balances[allocation.memberId] ?: 0L) - allocation.amountMinor
            }
        }

        for (settlement in settlements) {
            if (settlement.isDeleted) continue

            // Payer settles debt: balance increases (less negative or more positive)
            balances[settlement.payerMemberId] = (balances[settlement.payerMemberId] ?: 0L) + settlement.amountMinor

            // Recipient receives payment: balance decreases
            balances[settlement.recipientMemberId] = (balances[settlement.recipientMemberId] ?: 0L) - settlement.amountMinor
        }

        return balances.toMap()
    }

    /**
     * Calculates the total group expenditure from active expenses.
     */
    fun calculateTotalSpending(expenses: Collection<Expense>): Long {
        return expenses.filterNot { it.isDeleted }.sumOf { it.amountMinor }
    }
}

