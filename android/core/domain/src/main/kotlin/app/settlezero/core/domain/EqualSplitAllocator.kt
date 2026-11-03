package app.settlezero.core.domain

import app.settlezero.core.model.ExpenseSplitAllocation

/**
 * Distributes an expense amount equally across participants in a deterministic manner.
 *
 * Base share is floor(amount / N).
 * Remainder minor units (R = amount % N) are assigned to the first R participants
 * sorted strictly lexicographically by their immutable Member ID string.
 *
 * Invariant: Sum of allocations strictly equals amountMinor.
 */
object EqualSplitAllocator {

    fun allocate(amountMinor: Long, participantIds: Collection<String>): List<ExpenseSplitAllocation> {
        require(amountMinor > 0) { "Amount must be strictly positive: $amountMinor" }
        require(participantIds.isNotEmpty()) { "Participant list cannot be empty" }

        val sortedParticipants = participantIds.distinct().sorted()
        val n = sortedParticipants.size
        val baseShare = amountMinor / n
        val remainder = (amountMinor % n).toInt()

        return sortedParticipants.mapIndexed { index, memberId ->
            val extra = if (index < remainder) 1L else 0L
            ExpenseSplitAllocation(
                memberId = memberId,
                amountMinor = baseShare + extra
            )
        }
    }
}

