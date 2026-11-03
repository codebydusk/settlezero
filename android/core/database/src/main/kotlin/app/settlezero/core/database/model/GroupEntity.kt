package app.settlezero.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.settlezero.core.model.Group
import app.settlezero.core.model.GroupStatus

@Entity(
    tableName = "groups",
    indices = [
        Index(value = ["invite_token"], unique = true)
    ]
)
data class GroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "currency_scale")
    val currencyScale: Int,
    val status: String,
    @ColumnInfo(name = "invite_token")
    val inviteToken: String,
    @ColumnInfo(name = "is_currency_locked")
    val isCurrencyLocked: Boolean = false,
    @ColumnInfo(name = "is_read_only")
    val isReadOnly: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

fun GroupEntity.asDomain(): Group = Group(
    id = id,
    name = name,
    currencyCode = currencyCode,
    currencyScale = currencyScale,
    status = GroupStatus.valueOf(status),
    inviteToken = inviteToken,
    isCurrencyLocked = isCurrencyLocked,
    isReadOnly = isReadOnly,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Group.asEntity(): GroupEntity = GroupEntity(
    id = id,
    name = name,
    currencyCode = currencyCode,
    currencyScale = currencyScale,
    status = status.name,
    inviteToken = inviteToken,
    isCurrencyLocked = isCurrencyLocked,
    isReadOnly = isReadOnly,
    createdAt = createdAt,
    updatedAt = updatedAt
)

