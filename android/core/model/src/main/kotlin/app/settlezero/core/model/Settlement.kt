package app.settlezero.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Settlement(
    val id: String,
    val groupId: String,
    val payerMemberId: String,
    val recipientMemberId: String,
    val amountMinor: Long,
    val settlementDate: Long,
    val createdAt: Long,
    val note: String? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val deletedByMemberId: String? = null
) {
    init {
        require(amountMinor > 0) { "Settlement amount must be strictly positive: $amountMinor" }
        require(payerMemberId != recipientMemberId) { "Payer and recipient cannot be the same member: $payerMemberId" }
    }
}

