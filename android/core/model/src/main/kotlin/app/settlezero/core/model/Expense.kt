package app.settlezero.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SplitType {
    EQUAL,
    UNEQUAL
}

@Serializable
data class ExpenseSplitAllocation(
    val memberId: String,
    val amountMinor: Long
)

@Serializable
data class Expense(
    val id: String,
    val groupId: String,
    val description: String,
    val amountMinor: Long,
    val payerMemberId: String,
    val createdByMemberId: String,
    val expenseDate: Long,
    val createdAt: Long,
    val note: String? = null,
    val splitType: SplitType = SplitType.EQUAL,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val deletedByMemberId: String? = null,
    val allocations: List<ExpenseSplitAllocation> = emptyList()
) {
    init {
        require(amountMinor > 0) { "Expense amount must be strictly positive: $amountMinor" }
    }
}

