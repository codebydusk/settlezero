package app.settlezero.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityEventType {
    GROUP_CREATED,
    MEMBER_ADDED,
    MEMBER_ARCHIVED,
    EXPENSE_CREATED,
    EXPENSE_DELETED,
    SETTLEMENT_RECORDED,
    SETTLEMENT_DELETED,
    GROUP_ARCHIVED
}

@Serializable
data class ActivityHistory(
    val id: String,
    val groupId: String,
    val actorMemberId: String,
    val eventType: String,
    val summary: String,
    val entityId: String,
    val createdAt: Long
)

