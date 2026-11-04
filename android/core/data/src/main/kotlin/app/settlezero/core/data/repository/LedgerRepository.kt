package app.settlezero.core.data.repository

import app.settlezero.core.database.SettleZeroDatabase
import app.settlezero.core.database.model.asDomain
import app.settlezero.core.domain.DebtSimplifier
import app.settlezero.core.domain.NetBalanceProjector
import app.settlezero.core.model.DebtTransfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface LedgerRepository {
    fun observeGroupBalances(groupId: String): Flow<Map<String, Long>>
    fun observeSimplifiedDebts(groupId: String): Flow<List<DebtTransfer>>
    fun observeDirectDebts(groupId: String): Flow<List<DebtTransfer>>
    fun observeTotalSpending(groupId: String): Flow<Long>
    fun observePendingSyncCount(groupId: String): Flow<Int>
}

class OfflineFirstLedgerRepository(
    private val database: SettleZeroDatabase
) : LedgerRepository {

    private val memberDao = database.memberDao()
    private val expenseDao = database.expenseDao()
    private val settlementDao = database.settlementDao()
    private val syncQueueDao = database.syncQueueDao()

    private fun observeLedgerData(groupId: String) = combine(
        memberDao.observeMembers(groupId),
        expenseDao.observeActiveExpenses(groupId),
        settlementDao.observeActiveSettlements(groupId)
    ) { members, expenses, settlements ->
        Triple(
            members.map { it.id },
            expenses.map { it.asDomain() },
            settlements.map { it.asDomain() }
        )
    }

    override fun observeGroupBalances(groupId: String): Flow<Map<String, Long>> =
        observeLedgerData(groupId).map { (memberIds, expenses, settlements) ->
            NetBalanceProjector.calculateNetBalances(memberIds, expenses, settlements)
        }

    override fun observeSimplifiedDebts(groupId: String): Flow<List<DebtTransfer>> =
        observeGroupBalances(groupId).map { balances ->
            DebtSimplifier.simplify(balances)
        }

    override fun observeDirectDebts(groupId: String): Flow<List<DebtTransfer>> =
        observeLedgerData(groupId).map { (memberIds, expenses, settlements) ->
            DebtSimplifier.calculateDirectDebts(memberIds, expenses, settlements)
        }

    override fun observeTotalSpending(groupId: String): Flow<Long> =
        expenseDao.observeActiveExpenses(groupId).map { list ->
            NetBalanceProjector.calculateTotalSpending(list.map { it.asDomain() })
        }

    override fun observePendingSyncCount(groupId: String): Flow<Int> =
        syncQueueDao.observePendingCount(groupId)
}

