package app.settlezero.core.data.sync

import kotlinx.serialization.Serializable

object SyncOperationTypes {
    const val CREATE_GROUP = "CREATE_GROUP"
    const val ARCHIVE_GROUP = "ARCHIVE_GROUP"
    const val RESTORE_GROUP = "RESTORE_GROUP"
    const val ADD_MEMBER = "ADD_MEMBER"
    const val ARCHIVE_MEMBER = "ARCHIVE_MEMBER"
    const val CREATE_EXPENSE = "CREATE_EXPENSE"
    const val DELETE_EXPENSE = "DELETE_EXPENSE"
    const val RECORD_SETTLEMENT = "RECORD_SETTLEMENT"
    const val DELETE_SETTLEMENT = "DELETE_SETTLEMENT"
}

@Serializable
data class CreateGroupPayload(
    val groupId: String,
    val name: String,
    val currencyCode: String,
    val currencyScale: Int,
    val creatorMemberId: String,
    val creatorName: String,
    val inviteToken: String,
    val createdAt: Long
)

@Serializable
data class AddMemberPayload(
    val groupId: String,
    val memberId: String,
    val name: String,
    val role: String,
    val status: String,
    val addedByMemberId: String,
    val createdAt: Long
)

@Serializable
data class CreateExpensePayload(
    val groupId: String,
    val expenseId: String,
    val description: String,
    val amountMinor: Long,
    val payerMemberId: String,
    val createdByMemberId: String,
    val expenseDate: Long,
    val splitType: String,
    val note: String? = null,
    val allocations: List<ExpenseSplitAllocationPayload>,
    val createdAt: Long
)

@Serializable
data class ExpenseSplitAllocationPayload(
    val memberId: String,
    val amountMinor: Long
)

@Serializable
data class DeleteExpensePayload(
    val groupId: String,
    val expenseId: String,
    val deletedByMemberId: String,
    val deletedAt: Long
)

@Serializable
data class RecordSettlementPayload(
    val groupId: String,
    val settlementId: String,
    val payerMemberId: String,
    val recipientMemberId: String,
    val amountMinor: Long,
    val settlementDate: Long,
    val recordedByMemberId: String,
    val note: String? = null,
    val createdAt: Long
)

@Serializable
data class DeleteSettlementPayload(
    val groupId: String,
    val settlementId: String,
    val deletedByMemberId: String,
    val deletedAt: Long
)

