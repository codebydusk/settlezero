package app.settlezero

import android.app.Application
import android.content.Context
import app.settlezero.core.common.SystemTimeProvider
import app.settlezero.core.common.TimeProvider
import app.settlezero.core.data.repository.ExpenseRepository
import app.settlezero.core.data.repository.GroupRepository
import app.settlezero.core.data.repository.LedgerRepository
import app.settlezero.core.data.repository.MemberRepository
import app.settlezero.core.data.repository.DataPortabilityRepository
import app.settlezero.core.data.repository.HistoryRepository
import app.settlezero.core.data.repository.OfflineFirstDataPortabilityRepository
import app.settlezero.core.data.repository.OfflineFirstExpenseRepository
import app.settlezero.core.data.repository.OfflineFirstGroupRepository
import app.settlezero.core.data.repository.OfflineFirstHistoryRepository
import app.settlezero.core.data.repository.OfflineFirstLedgerRepository
import app.settlezero.core.data.repository.OfflineFirstMemberRepository
import app.settlezero.core.data.repository.OfflineFirstSettlementRepository
import app.settlezero.core.data.repository.SettlementRepository
import app.settlezero.core.database.SettleZeroDatabase
import app.settlezero.core.sync.SyncManager
import app.settlezero.core.sync.WorkManagerSyncManager

interface AppContainer {
    val database: SettleZeroDatabase
    val timeProvider: TimeProvider
    val groupRepository: GroupRepository
    val memberRepository: MemberRepository
    val expenseRepository: ExpenseRepository
    val settlementRepository: SettlementRepository
    val ledgerRepository: LedgerRepository
    val historyRepository: HistoryRepository
    val dataPortabilityRepository: DataPortabilityRepository
    val syncManager: SyncManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: SettleZeroDatabase by lazy {
        SettleZeroDatabase.buildDatabase(context)
    }

    override val timeProvider: TimeProvider by lazy {
        SystemTimeProvider()
    }

    override val groupRepository: GroupRepository by lazy {
        OfflineFirstGroupRepository(database, timeProvider)
    }

    override val memberRepository: MemberRepository by lazy {
        OfflineFirstMemberRepository(database, timeProvider)
    }

    override val expenseRepository: ExpenseRepository by lazy {
        OfflineFirstExpenseRepository(database, timeProvider)
    }

    override val settlementRepository: SettlementRepository by lazy {
        OfflineFirstSettlementRepository(database, timeProvider)
    }

    override val ledgerRepository: LedgerRepository by lazy {
        OfflineFirstLedgerRepository(database)
    }

    override val historyRepository: HistoryRepository by lazy {
        OfflineFirstHistoryRepository(database)
    }

    override val dataPortabilityRepository: DataPortabilityRepository by lazy {
        OfflineFirstDataPortabilityRepository(database, timeProvider)
    }

    override val syncManager: SyncManager by lazy {
        WorkManagerSyncManager(context)
    }
}

class SettleZeroApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
