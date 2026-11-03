package app.settlezero.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.settlezero.core.database.model.SettlementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettlementDao {

    @Query("SELECT * FROM settlements WHERE group_id = :groupId ORDER BY settlement_date DESC, created_at DESC")
    fun observeSettlements(groupId: String): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements WHERE group_id = :groupId AND is_deleted = 0 ORDER BY settlement_date DESC, created_at DESC")
    fun observeActiveSettlements(groupId: String): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements WHERE group_id = :groupId ORDER BY settlement_date DESC, created_at DESC")
    suspend fun getSettlements(groupId: String): List<SettlementEntity>

    @Query("SELECT * FROM settlements WHERE group_id = :groupId AND is_deleted = 0 ORDER BY settlement_date DESC, created_at DESC")
    suspend fun getActiveSettlements(groupId: String): List<SettlementEntity>

    @Query("SELECT * FROM settlements WHERE id = :id")
    suspend fun getSettlementById(id: String): SettlementEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSettlement(settlement: SettlementEntity)

    @Query("UPDATE settlements SET is_deleted = 1, deleted_by_member_id = :deletedByMemberId, deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDeleteSettlement(id: String, deletedByMemberId: String, deletedAt: Long)

    @Query("UPDATE settlements SET is_deleted = 0, deleted_by_member_id = NULL, deleted_at = NULL WHERE id = :id")
    suspend fun restoreSettlement(id: String)
}

