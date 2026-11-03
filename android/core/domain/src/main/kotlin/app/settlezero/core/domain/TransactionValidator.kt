package app.settlezero.core.domain

import app.settlezero.core.model.Expense
import app.settlezero.core.model.Member
import app.settlezero.core.model.Settlement
import app.settlezero.core.model.SplitType

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult {
        override val isValid: Boolean get() = false
    }
    val isValid: Boolean get() = this is Valid
}

object TransactionValidator {

    /**
     * Validates an expense against group members and financial rules.
     */
    fun validateExpense(
        expense: Expense,
        members: Map<String, Member>
    ): ValidationResult {
        if (expense.amountMinor <= 0) {
            return ValidationResult.Invalid("Expense amount must be greater than zero.")
        }

        if (expense.description.isBlank()) {
            return ValidationResult.Invalid("Expense description cannot be empty.")
        }

        val payer = members[expense.payerMemberId]
            ?: return ValidationResult.Invalid("Payer is not a recognized member of this group.")

        if (payer.isArchived) {
            return ValidationResult.Invalid("Archived member '${payer.name}' cannot pay for new expenses.")
        }

        if (expense.allocations.isEmpty()) {
            return ValidationResult.Invalid("At least one participant must be selected.")
        }

        for (allocation in expense.allocations) {
            val participant = members[allocation.memberId]
                ?: return ValidationResult.Invalid("Participant is not a member of this group.")

            if (participant.isArchived) {
                return ValidationResult.Invalid("Archived member '${participant.name}' cannot participate in new expenses.")
            }

            if (expense.splitType == SplitType.UNEQUAL && allocation.amountMinor <= 0) {
                return ValidationResult.Invalid("Zero or negative allocations are not allowed in unequal splits. Remove the participant instead.")
            }
        }

        val totalAllocated = expense.allocations.sumOf { it.amountMinor }
        if (totalAllocated != expense.amountMinor) {
            return ValidationResult.Invalid("Allocated split sum ($totalAllocated) does not match total amount (${expense.amountMinor}).")
        }

        return ValidationResult.Valid
    }

    /**
     * Validates a settlement against current outstanding debt.
     * Overpayments (settlement amount exceeding outstanding debt) are strictly rejected.
     */
    fun validateSettlement(
        settlement: Settlement,
        members: Map<String, Member>,
        maxOutstandingDebt: Long
    ): ValidationResult {
        if (settlement.amountMinor <= 0) {
            return ValidationResult.Invalid("Settlement amount must be greater than zero.")
        }

        if (settlement.payerMemberId == settlement.recipientMemberId) {
            return ValidationResult.Invalid("Self-settlements are not allowed.")
        }

        val payer = members[settlement.payerMemberId]
            ?: return ValidationResult.Invalid("Payer is not a recognized member of this group.")

        val recipient = members[settlement.recipientMemberId]
            ?: return ValidationResult.Invalid("Recipient is not a recognized member of this group.")

        if (maxOutstandingDebt <= 0) {
            return ValidationResult.Invalid("${payer.name} does not currently owe ${recipient.name} any money.")
        }

        if (settlement.amountMinor > maxOutstandingDebt) {
            return ValidationResult.Invalid(
                "Settlement amount (${settlement.amountMinor}) exceeds outstanding debt ($maxOutstandingDebt) between ${payer.name} and ${recipient.name}."
            )
        }

        return ValidationResult.Valid
    }
}

