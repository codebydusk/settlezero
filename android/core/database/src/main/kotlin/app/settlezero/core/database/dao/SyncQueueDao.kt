package app.settlezero.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.settlezero.core.database.model.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue WHERE group_id = :groupId ORDER BY created_at ASC")
    fun observeQueueForGroup(groupId: String): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingOperations(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE group_id = :groupId AND status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingOperationsForGroup(groupId: String): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE group_id = :groupId AND status = 'PENDING'")
    fun observePendingCount(groupId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAll(items: List<SyncQueueEntity>)

    @Query("UPDATE sync_queue SET status = :status, error_message = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, errorMessage: String? = null)

    @Query("UPDATE sync_queue SET attempt_count = attempt_count + 1 WHERE id = :id")
    suspend fun incrementAttempt(id: String)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteOperation(id: String)

    @Query("DELETE FROM sync_queue WHERE idempotency_key = :key")
    suspend fun deleteByIdempotencyKey(key: String)
}

