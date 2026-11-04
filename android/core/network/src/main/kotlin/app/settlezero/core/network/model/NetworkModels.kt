package app.settlezero.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class NetworkSyncOperation(
    val idempotencyKey: String,
    val groupId: String,
    val operationType: String,
    val payloadJson: String,
    val createdAt: Long
)

@Serializable
data class NetworkSyncBatchRequest(
    val operations: List<NetworkSyncOperation>
)

@Serializable
data class NetworkSyncResult(
    val idempotencyKey: String,
    val status: String,
    val errorCode: String? = null,
    val errorMessage: String? = null
)

@Serializable
data class NetworkSyncBatchResponse(
    val results: List<NetworkSyncResult>
)

@Serializable
data class NetworkExpenseSplitAllocationDto(
    val memberId: String,
    val amountMinor: Long
)

@Serializable
data class NetworkExpenseDto(
    val id: String,
    val groupId: String,
    val description: String,
    val amountMinor: Long,
    val payerMemberId: String,
    val createdByMemberId: String,
    val expenseDate: Long,
    val createdAt: Long,
    val note: String? = null,
    val splitType: String,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val deletedByMemberId: String? = null,
    val allocations: List<NetworkExpenseSplitAllocationDto> = emptyList()
)

@Serializable
data class NetworkSettlementDto(
    val id: String,
    val groupId: String,
    val payerMemberId: String,
    val recipientMemberId: String,
    val amountMinor: Long,
    val settlementDate: Long,
    val createdAt: Long,
    val recordedByMemberId: String,
    val note: String? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val deletedByMemberId: String? = null
)

@Serializable
data class NetworkMemberDto(
    val id: String,
    val groupId: String,
    val name: String,
    val role: String,
    val status: String,
    val createdAt: Long
)

@Serializable
data class NetworkGroupDto(
    val id: String,
    val name: String,
    val currencyCode: String,
    val currencyScale: Int,
    val status: String,
    val inviteToken: String,
    val isCurrencyLocked: Boolean = false,
    val isReadOnly: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class NetworkActivityHistoryDto(
    val id: String,
    val groupId: String,
    val actorMemberId: String,
    val eventType: String,
    val summary: String,
    val entityId: String,
    val createdAt: Long
)

@Serializable
data class NetworkGroupSnapshot(
    val group: NetworkGroupDto,
    val members: List<NetworkMemberDto> = emptyList(),
    val expenses: List<NetworkExpenseDto> = emptyList(),
    val settlements: List<NetworkSettlementDto> = emptyList(),
    val activityHistory: List<NetworkActivityHistoryDto> = emptyList()
)

@Serializable
data class NetworkInviteMetadata(
    val groupId: String,
    val groupName: String,
    val currencyCode: String,
    val currencyScale: Int,
    val createdAt: Long
)

@Serializable
data class NetworkClaimRequest(
    val memberId: String,
    val claimantDeviceId: String,
    val sasCode: String
)

@Serializable
data class NetworkClaimResponse(
    val status: String,
    val claimId: String? = null
)

