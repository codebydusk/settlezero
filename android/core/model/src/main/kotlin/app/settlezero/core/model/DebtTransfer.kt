package app.settlezero.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DebtTransfer(
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long
) {
    init {
        require(amountMinor > 0) { "Transfer amount must be strictly positive: $amountMinor" }
        require(fromMemberId != toMemberId) { "Transfer debtor and creditor cannot be identical: $fromMemberId" }
    }
}

