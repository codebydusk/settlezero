package app.settlezero.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.settlezero.core.model.Settlement

@Entity(
    tableName = "settlements",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["payer_member_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipient_member_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("group_id"),
        Index("payer_member_id"),
        Index("recipient_member_id")
    ]
)
data class SettlementEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "group_id")
    val groupId: String,
    @ColumnInfo(name = "payer_member_id")
    val payerMemberId: String,
    @ColumnInfo(name = "recipient_member_id")
    val recipientMemberId: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "settlement_date")
    val settlementDate: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    val note: String? = null,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_by_member_id")
    val deletedByMemberId: String? = null
)

fun SettlementEntity.asDomain(): Settlement = Settlement(
    id = id,
    groupId = groupId,
    payerMemberId = payerMemberId,
    recipientMemberId = recipientMemberId,
    amountMinor = amountMinor,
    settlementDate = settlementDate,
    createdAt = createdAt,
    note = note,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    deletedByMemberId = deletedByMemberId
)

fun Settlement.asEntity(): SettlementEntity = SettlementEntity(
    id = id,
    groupId = groupId,
    payerMemberId = payerMemberId,
    recipientMemberId = recipientMemberId,
    amountMinor = amountMinor,
    settlementDate = settlementDate,
    createdAt = createdAt,
    note = note,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    deletedByMemberId = deletedByMemberId
)

