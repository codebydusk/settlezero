package app.settlezero.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.settlezero.core.model.Member
import app.settlezero.core.model.MemberRole
import app.settlezero.core.model.MemberStatus

@Entity(
    tableName = "members",
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
        Index(value = ["group_id", "name"], unique = true)
    ]
)
data class MemberEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "group_id")
    val groupId: String,
    val name: String,
    val role: String,
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

fun MemberEntity.asDomain(): Member = Member(
    id = id,
    groupId = groupId,
    name = name,
    role = MemberRole.valueOf(role),
    status = MemberStatus.valueOf(status),
    createdAt = createdAt
)

fun Member.asEntity(): MemberEntity = MemberEntity(
    id = id,
    groupId = groupId,
    name = name,
    role = role.name,
    status = status.name,
    createdAt = createdAt
)

