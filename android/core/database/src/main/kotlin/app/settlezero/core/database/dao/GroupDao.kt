package app.settlezero.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.settlezero.core.database.model.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM groups WHERE status = 'ACTIVE' ORDER BY updated_at DESC")
    fun observeActiveGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE status = 'ARCHIVED' ORDER BY updated_at DESC")
    fun observeArchivedGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    fun observeGroupById(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getGroupById(id: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE invite_token = :token")
    suspend fun getGroupByInviteToken(token: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: GroupEntity)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Query("UPDATE groups SET is_currency_locked = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun lockCurrency(id: String, updatedAt: Long)

    @Query("UPDATE groups SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteGroup(id: String)
}

