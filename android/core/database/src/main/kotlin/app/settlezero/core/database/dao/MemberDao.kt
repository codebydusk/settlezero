package app.settlezero.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.settlezero.core.database.model.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {

    @Query("SELECT * FROM members WHERE group_id = :groupId ORDER BY name COLLATE NOCASE ASC")
    fun observeMembers(groupId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE group_id = :groupId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getMembers(groupId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getMemberById(id: String): MemberEntity?

    @Query("SELECT * FROM members WHERE group_id = :groupId AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getMemberByName(groupId: String, name: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMember(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMembers(members: List<MemberEntity>)

    @Update
    suspend fun updateMember(member: MemberEntity)

    @Query("UPDATE members SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE members SET role = :role WHERE id = :id")
    suspend fun updateRole(id: String, role: String)
}

