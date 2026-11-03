package app.settlezero.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class GroupStatus {
    ACTIVE,
    ARCHIVED
}

@Serializable
data class Group(
    val id: String,
    val name: String,
    val currencyCode: String,
    val currencyScale: Int,
    val status: GroupStatus = GroupStatus.ACTIVE,
    val inviteToken: String,
    val isCurrencyLocked: Boolean = false,
    val isReadOnly: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isActive: Boolean get() = status == GroupStatus.ACTIVE
    val isArchived: Boolean get() = status == GroupStatus.ARCHIVED
}

