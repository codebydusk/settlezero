package app.settlezero.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.settlezero.core.database.model.ExpenseEntity
import app.settlezero.core.database.model.ExpenseSplitEntity
import app.settlezero.core.database.model.PopulatedExpense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Transaction
    @Query("SELECT * FROM expenses WHERE group_id = :groupId ORDER BY expense_date DESC, created_at DESC")
    fun observeExpenses(groupId: String): Flow<List<PopulatedExpense>>

    @Transaction
    @Query("SELECT * FROM expenses WHERE group_id = :groupId AND is_deleted = 0 ORDER BY expense_date DESC, created_at DESC")
    fun observeActiveExpenses(groupId: String): Flow<List<PopulatedExpense>>

    @Transaction
    @Query("SELECT * FROM expenses WHERE group_id = :groupId ORDER BY expense_date DESC, created_at DESC")
    suspend fun getExpenses(groupId: String): List<PopulatedExpense>

    @Transaction
    @Query("SELECT * FROM expenses WHERE group_id = :groupId AND is_deleted = 0 ORDER BY expense_date DESC, created_at DESC")
    suspend fun getActiveExpenses(groupId: String): List<PopulatedExpense>

    @Transaction
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): PopulatedExpense?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplits(splits: List<ExpenseSplitEntity>)

    @Query("DELETE FROM expense_splits WHERE expense_id = :expenseId")
    suspend fun deleteSplitsForExpense(expenseId: String)

    @Transaction
    suspend fun insertExpenseWithSplits(expense: ExpenseEntity, splits: List<ExpenseSplitEntity>) {
        insertExpense(expense)
        insertSplits(splits)
    }

    @Transaction
    suspend fun updateExpenseWithSplits(expense: ExpenseEntity, splits: List<ExpenseSplitEntity>) {
        updateExpense(expense)
        deleteSplitsForExpense(expense.id)
        insertSplits(splits)
    }

    @Query("UPDATE expenses SET is_deleted = 1, deleted_by_member_id = :deletedByMemberId, deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDeleteExpense(id: String, deletedByMemberId: String, deletedAt: Long)

    @Query("UPDATE expenses SET is_deleted = 0, deleted_by_member_id = NULL, deleted_at = NULL WHERE id = :id")
    suspend fun restoreExpense(id: String)
}

