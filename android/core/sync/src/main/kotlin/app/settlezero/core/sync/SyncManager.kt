package app.settlezero.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

interface SyncManager {
    fun scheduleBackgroundSync(groupId: String)
    fun cancelSync(groupId: String)
    fun observeIsSyncing(groupId: String): Flow<Boolean>
}

class WorkManagerSyncManager(
    private val context: Context
) : SyncManager {

    private val workManager = WorkManager.getInstance(context)

    override fun scheduleBackgroundSync(groupId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "${SyncWorker.WORK_NAME_PREFIX}$groupId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncWork
        )
    }

    override fun cancelSync(groupId: String) {
        workManager.cancelUniqueWork("${SyncWorker.WORK_NAME_PREFIX}$groupId")
    }

    override fun observeIsSyncing(groupId: String): Flow<Boolean> {
        return workManager.getWorkInfosForUniqueWorkFlow("${SyncWorker.WORK_NAME_PREFIX}$groupId")
            .map { list ->
                list.any { it.state == WorkInfo.State.RUNNING }
            }
    }
}

