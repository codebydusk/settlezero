package app.settlezero.core.domain

import app.settlezero.core.model.DebtTransfer
import app.settlezero.core.model.Expense
import app.settlezero.core.model.Settlement

/**
 * Derives repayment relationships across group members.
 * Supports both Simplified (greedy min-cash-flow with deterministic tie-breaking)
 * and Direct/Raw pairwise debt relationships.
 */
object DebtSimplifier {

    private data class Debtor(val memberId: String, var debt: Long)
    private data class Creditor(val memberId: String, var credit: Long)

    /**
     * Calculates the minimum practical set of transfers to clear all net debts.
     * Applies a strict deterministic greedy matching algorithm:
     * - Debtors sorted ascending by debt amount; ties broken by Member ID string ascending.
     * - Creditors sorted descending by credit amount; ties broken by Member ID string ascending.
     * Guarantees 100% identical repayment graphs across all distributed clients.
     */
    fun simplify(netBalances: Map<String, Long>): List<DebtTransfer> {
        val debtors = netBalances.filter { it.value < 0 }
            .map { (id, balance) -> Debtor(id, -balance) }
            .sortedWith(compareBy<Debtor> { it.debt }.thenBy { it.memberId })
            .toMutableList()

        val creditors = netBalances.filter { it.value > 0 }
            .map { (id, balance) -> Creditor(id, balance) }
            .sortedWith(compareByDescending<Creditor> { it.credit }.thenBy { it.memberId })
            .toMutableList()

        val transfers = mutableListOf<DebtTransfer>()
        var dIdx = 0
        var cIdx = 0

        while (dIdx < debtors.size && cIdx < creditors.size) {
            val debtor = debtors[dIdx]
            val creditor = creditors[cIdx]

            val settledAmount = minOf(debtor.debt, creditor.credit)
            if (settledAmount > 0) {
                transfers.add(
                    DebtTransfer(
                        fromMemberId = debtor.memberId,
                        toMemberId = creditor.memberId,
                        amountMinor = settledAmount
                    )
                )
            }

            debtor.debt -= settledAmount
            creditor.credit -= settledAmount

            if (debtor.debt == 0L) dIdx++
            if (creditor.credit == 0L) cIdx++
        }

        return transfers
    }

    /**
     * Calculates direct (unsimplified) pairwise debts resulting directly from transactions.
     * Useful when users toggle "Simplify Debts = OFF" to inspect exact pairwise transactions.
     */
    fun calculateDirectDebts(
        memberIds: Collection<String>,
        expenses: Collection<Expense>,
        settlements: Collection<Settlement>
    ): List<DebtTransfer> {
        val pairwise = mutableMapOf<Pair<String, String>, Long>()

        for (expense in expenses) {
            if (expense.isDeleted) continue
            val payerId = expense.payerMemberId
            for (allocation in expense.allocations) {
                val participantId = allocation.memberId
                if (participantId != payerId) {
                    val key = participantId to payerId
                    pairwise[key] = (pairwise[key] ?: 0L) + allocation.amountMinor
                }
            }
        }

        for (settlement in settlements) {
            if (settlement.isDeleted) continue
            val key = settlement.payerMemberId to settlement.recipientMemberId
            pairwise[key] = (pairwise[key] ?: 0L) - settlement.amountMinor
        }

        val results = mutableListOf<DebtTransfer>()
        val allMembers = memberIds.distinct().sorted()

        for (i in 0 until allMembers.size) {
            for (j in i + 1 until allMembers.size) {
                val m1 = allMembers[i]
                val m2 = allMembers[j]

                val m1OwesM2 = pairwise[m1 to m2] ?: 0L
                val m2OwesM1 = pairwise[m2 to m1] ?: 0L
                val net = m1OwesM2 - m2OwesM1

                if (net > 0) {
                    results.add(DebtTransfer(fromMemberId = m1, toMemberId = m2, amountMinor = net))
                } else if (net < 0) {
                    results.add(DebtTransfer(fromMemberId = m2, toMemberId = m1, amountMinor = -net))
                }
            }
        }

        return results.sortedWith(
            compareBy<DebtTransfer> { it.fromMemberId }
                .thenBy { it.toMemberId }
        )
    }
}
