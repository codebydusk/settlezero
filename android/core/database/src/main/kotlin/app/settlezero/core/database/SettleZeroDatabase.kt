package app.settlezero.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.settlezero.core.database.dao.ActivityHistoryDao
import app.settlezero.core.database.dao.ExpenseDao
import app.settlezero.core.database.dao.GroupDao
import app.settlezero.core.database.dao.MemberDao
import app.settlezero.core.database.dao.SettlementDao
import app.settlezero.core.database.dao.SyncQueueDao
import app.settlezero.core.database.model.ActivityHistoryEntity
import app.settlezero.core.database.model.ExpenseEntity
import app.settlezero.core.database.model.ExpenseSplitEntity
import app.settlezero.core.database.model.GroupEntity
import app.settlezero.core.database.model.MemberEntity
import app.settlezero.core.database.model.SettlementEntity
import app.settlezero.core.database.model.SyncQueueEntity

@Database(
    entities = [
        GroupEntity::class,
        MemberEntity::class,
        ExpenseEntity::class,
        ExpenseSplitEntity::class,
        SettlementEntity::class,
        SyncQueueEntity::class,
        ActivityHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SettleZeroDatabase : RoomDatabase() {

    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun settlementDao(): SettlementDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun activityHistoryDao(): ActivityHistoryDao

    companion object {
        const val DATABASE_NAME = "settlezero.db"

        fun buildDatabase(context: Context): SettleZeroDatabase =
            Room.databaseBuilder(context, SettleZeroDatabase::class.java, DATABASE_NAME)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    }
                })
                .fallbackToDestructiveMigration()
                .build()

        fun buildInMemory(context: Context): SettleZeroDatabase =
            Room.inMemoryDatabaseBuilder(context, SettleZeroDatabase::class.java)
                .allowMainThreadQueries()
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    }
                })
                .build()
    }
}

