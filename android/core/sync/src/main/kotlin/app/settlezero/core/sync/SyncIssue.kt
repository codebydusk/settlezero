package app.settlezero.core.sync

import kotlinx.serialization.Serializable

@Serializable
enum class SyncStatus {
    SYNCED,
    PENDING,
    SYNCING,
    REJECTED,
    CONFLICT
}

@Serializable
enum class SyncRejectionReason {
    GROUP_ARCHIVED,
    PERMISSION_CHANGED,
    NAME_CONFLICT,
    SETTLEMENT_EXCEEDS_DEBT,
    SERVER_ERROR,
    NETWORK_ERROR
}

@Serializable
data class SyncIssue(
    val operationId: String,
    val groupId: String,
    val operationType: String,
    val rejectionReason: SyncRejectionReason,
    val message: String,
    val payloadJson: String,
    val timestamp: Long
)

