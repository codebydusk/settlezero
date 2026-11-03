package app.settlezero.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.settlezero.core.database.model.ActivityHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityHistoryDao {

    @Query("SELECT * FROM activity_history WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observeHistory(groupId: String): Flow<List<ActivityHistoryEntity>>

    @Query("SELECT * FROM activity_history WHERE group_id = :groupId ORDER BY created_at DESC")
    suspend fun getHistory(groupId: String): List<ActivityHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ActivityHistoryEntity)
}

