package app.settlezero.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
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
            childColumns = ["created_by_member_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("group_id"),
        Index("payer_member_id"),
        Index("created_by_member_id")
    ]
)
data class ExpenseEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "group_id")
    val groupId: String,
    val description: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "payer_member_id")
    val payerMemberId: String,
    @ColumnInfo(name = "created_by_member_id")
    val createdByMemberId: String,
    @ColumnInfo(name = "expense_date")
    val expenseDate: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    val note: String? = null,
    @ColumnInfo(name = "split_type")
    val splitType: String,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_by_member_id")
    val deletedByMemberId: String? = null
)

