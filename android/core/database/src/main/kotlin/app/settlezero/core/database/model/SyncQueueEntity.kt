package app.settlezero.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("group_id"),
        Index(value = ["idempotency_key"], unique = true)
    ]
)
data class SyncQueueEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "group_id")
    val groupId: String,
    @ColumnInfo(name = "operation_type")
    val operationType: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    val status: String, // "PENDING", "IN_FLIGHT", "REJECTED"
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

