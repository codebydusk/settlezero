package app.settlezero.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.settlezero.core.database.SettleZeroDatabase

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = SettleZeroDatabase.buildDatabase(applicationContext)
        val syncQueueDao = database.syncQueueDao()

        return try {
            val pendingOps = syncQueueDao.getPendingOperations()
            if (pendingOps.isEmpty()) {
                return Result.success()
            }

            for (op in pendingOps) {
                // In local-first offline simulation/mock, operations transition
                // or await remote sync client in core:network.
                syncQueueDao.updateStatus(op.id, "IN_FLIGHT")

                // Mark processed/synced for now or retry on network failure
                syncQueueDao.deleteOperation(op.id)
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME_PREFIX = "sync_group_"
    }
}

