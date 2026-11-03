package app.settlezero.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MemberRole {
    SUPER_ADMIN,
    ADMIN,
    MEMBER
}

@Serializable
enum class MemberStatus {
    ACTIVE_CLAIMED,
    ACTIVE_UNCLAIMED,
    ARCHIVED
}

@Serializable
data class Member(
    val id: String,
    val groupId: String,
    val name: String,
    val role: MemberRole,
    val status: MemberStatus,
    val createdAt: Long
) {
    val isActive: Boolean get() = status != MemberStatus.ARCHIVED
    val isArchived: Boolean get() = status == MemberStatus.ARCHIVED
    val isClaimed: Boolean get() = status == MemberStatus.ACTIVE_CLAIMED
    val isUnclaimed: Boolean get() = status == MemberStatus.ACTIVE_UNCLAIMED
    val isAdmin: Boolean get() = role == MemberRole.ADMIN || role == MemberRole.SUPER_ADMIN
    val isSuperAdmin: Boolean get() = role == MemberRole.SUPER_ADMIN

    /**
     * Active members (claimed or unclaimed) can participate in new expenses and be payers.
     * Archived members retain historical financial relations but cannot participate in new expenses.
     */
    val canParticipateInNewExpenses: Boolean get() = isActive
}

