package app.settlezero.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.settlezero.core.database.SettleZeroDatabase
import app.settlezero.core.network.SyncApiClient
import app.settlezero.core.network.SyncNetworkDataSource
import app.settlezero.core.network.model.NetworkSyncOperation

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val networkDataSource: SyncNetworkDataSource = SyncApiClient(),
    private val dbInstance: SettleZeroDatabase? = null
) : CoroutineWorker(appContext, workerParams) {

    // Default constructor for WorkManager's DefaultWorkerFactory
    constructor(appContext: Context, workerParams: WorkerParameters) : this(
        appContext = appContext,
        workerParams = workerParams,
        networkDataSource = SyncApiClient(),
        dbInstance = null
    )

    override suspend fun doWork(): Result {
        val database = dbInstance ?: SettleZeroDatabase.buildDatabase(applicationContext)
        val syncQueueDao = database.syncQueueDao()

        return try {
            val pendingOps = syncQueueDao.getPendingOperations()
            if (pendingOps.isEmpty()) {
                return Result.success()
            }

            // Mark operations as IN_FLIGHT
            for (op in pendingOps) {
                syncQueueDao.updateStatus(op.id, "IN_FLIGHT")
            }

            val networkOps = pendingOps.map { op ->
                NetworkSyncOperation(
                    idempotencyKey = op.idempotencyKey,
                    groupId = op.groupId,
                    operationType = op.operationType,
                    payloadJson = op.payloadJson,
                    createdAt = op.createdAt
                )
            }

            val syncResult = networkDataSource.syncBatch(networkOps)
            if (syncResult.isFailure) {
                // Network failure: revert to PENDING and increment attempt count
                for (op in pendingOps) {
                    syncQueueDao.incrementAttempt(op.id)
                    syncQueueDao.updateStatus(op.id, "PENDING", syncResult.exceptionOrNull()?.message)
                }
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            val batchResponse = syncResult.getOrThrow()
            val resultMap = batchResponse.results.associateBy { it.idempotencyKey }

            for (op in pendingOps) {
                val itemResult = resultMap[op.idempotencyKey]
                if (itemResult != null) {
                    when (itemResult.status) {
                        "ACCEPTED", "DUPLICATE" -> {
                            syncQueueDao.deleteOperation(op.id)
                        }
                        "REJECTED" -> {
                            syncQueueDao.updateStatus(
                                op.id,
                                "REJECTED",
                                itemResult.errorMessage ?: itemResult.errorCode ?: "Rejected by sync server"
                            )
                        }
                        else -> {
                            syncQueueDao.updateStatus(op.id, "PENDING")
                        }
                    }
                } else {
                    syncQueueDao.updateStatus(op.id, "PENDING")
                }
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
