package app.settlezero.core.data.repository

import app.settlezero.core.database.SettleZeroDatabase
import app.settlezero.core.database.model.asDomain
import app.settlezero.core.model.ActivityHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface HistoryRepository {
    fun observeHistory(groupId: String): Flow<List<ActivityHistory>>
    suspend fun getHistory(groupId: String): List<ActivityHistory>
}

class OfflineFirstHistoryRepository(
    private val database: SettleZeroDatabase
) : HistoryRepository {

    private val historyDao = database.activityHistoryDao()

    override fun observeHistory(groupId: String): Flow<List<ActivityHistory>> =
        historyDao.observeHistory(groupId).map { list -> list.map { it.asDomain() } }

    override suspend fun getHistory(groupId: String): List<ActivityHistory> =
        historyDao.getHistory(groupId).map { it.asDomain() }
}

