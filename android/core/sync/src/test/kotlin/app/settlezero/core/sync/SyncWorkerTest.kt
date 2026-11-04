package app.settlezero.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.settlezero.core.database.SettleZeroDatabase
import app.settlezero.core.database.model.GroupEntity
import app.settlezero.core.database.model.SyncQueueEntity
import app.settlezero.core.network.SyncNetworkDataSource
import app.settlezero.core.network.model.NetworkClaimResponse
import app.settlezero.core.network.model.NetworkGroupSnapshot
import app.settlezero.core.network.model.NetworkInviteMetadata
import app.settlezero.core.network.model.NetworkSyncBatchResponse
import app.settlezero.core.network.model.NetworkSyncOperation
import app.settlezero.core.network.model.NetworkSyncResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncWorkerTest {

    private lateinit var context: Context
    private lateinit var database: SettleZeroDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = SettleZeroDatabase.buildDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createWorker(networkDataSource: SyncNetworkDataSource): SyncWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return SyncWorker(appContext, workerParameters, networkDataSource, database)
            }
        }
        return TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun `syncWorker processes and clears accepted operations`() = runTest {
        val group = GroupEntity(
            id = "grp-1",
            name = "Trip",
            currencyCode = "INR",
            currencyScale = 2,
            status = "ACTIVE",
            inviteToken = "token",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        database.groupDao().insertGroup(group)

        val op = SyncQueueEntity(
            id = "op-1",
            groupId = "grp-1",
            operationType = "CREATE_EXPENSE",
            payloadJson = "{}",
            idempotencyKey = "key-1",
            status = "PENDING",
            createdAt = 1000L
        )
        database.syncQueueDao().enqueue(op)

        assertThat(database.syncQueueDao().getPendingOperations()).hasSize(1)

        val fakeNetwork = object : FakeSyncNetworkDataSource() {
            override suspend fun syncBatch(operations: List<NetworkSyncOperation>): Result<NetworkSyncBatchResponse> {
                return Result.success(
                    NetworkSyncBatchResponse(
                        results = operations.map {
                            NetworkSyncResult(idempotencyKey = it.idempotencyKey, status = "ACCEPTED")
                        }
                    )
                )
            }
        }

        val worker = createWorker(fakeNetwork)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(database.syncQueueDao().getPendingOperations()).isEmpty()
    }

    @Test
    fun `syncWorker handles rejected operations without crashing`() = runTest {
        val group = GroupEntity(
            id = "grp-1",
            name = "Trip",
            currencyCode = "INR",
            currencyScale = 2,
            status = "ACTIVE",
            inviteToken = "token",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        database.groupDao().insertGroup(group)

        val op = SyncQueueEntity(
            id = "op-2",
            groupId = "grp-1",
            operationType = "CREATE_EXPENSE",
            payloadJson = "{}",
            idempotencyKey = "key-2",
            status = "PENDING",
            createdAt = 1000L
        )
        database.syncQueueDao().enqueue(op)

        val fakeNetwork = object : FakeSyncNetworkDataSource() {
            override suspend fun syncBatch(operations: List<NetworkSyncOperation>): Result<NetworkSyncBatchResponse> {
                return Result.success(
                    NetworkSyncBatchResponse(
                        results = listOf(
                            NetworkSyncResult(
                                idempotencyKey = "key-2",
                                status = "REJECTED",
                                errorCode = "GROUP_ARCHIVED",
                                errorMessage = "Group is archived"
                            )
                        )
                    )
                )
            }
        }

        val worker = createWorker(fakeNetwork)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        // No longer in pending
        assertThat(database.syncQueueDao().getPendingOperations()).isEmpty()

        // Status is updated to REJECTED in database
        val allOps = database.syncQueueDao().observeQueueForGroup("grp-1").first()
        assertThat(allOps).hasSize(1)
        assertThat(allOps[0].status).isEqualTo("REJECTED")
        assertThat(allOps[0].errorMessage).contains("Group is archived")
    }

    @Test
    fun `syncWorker retries on network failure`() = runTest {
        val group = GroupEntity(
            id = "grp-1",
            name = "Trip",
            currencyCode = "INR",
            currencyScale = 2,
            status = "ACTIVE",
            inviteToken = "token",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        database.groupDao().insertGroup(group)

        val op = SyncQueueEntity(
            id = "op-3",
            groupId = "grp-1",
            operationType = "CREATE_EXPENSE",
            payloadJson = "{}",
            idempotencyKey = "key-3",
            status = "PENDING",
            createdAt = 1000L
        )
        database.syncQueueDao().enqueue(op)

        val failingNetwork = object : FakeSyncNetworkDataSource() {
            override suspend fun syncBatch(operations: List<NetworkSyncOperation>): Result<NetworkSyncBatchResponse> {
                return Result.failure(IOException("Connection refused"))
            }
        }

        val worker = createWorker(failingNetwork)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
        val pending = database.syncQueueDao().getPendingOperations()
        assertThat(pending).hasSize(1)
        assertThat(pending[0].attemptCount).isEqualTo(1)
        assertThat(pending[0].errorMessage).contains("Connection refused")
    }

    open class FakeSyncNetworkDataSource : SyncNetworkDataSource {
        override suspend fun healthCheck(): Result<Boolean> = Result.success(true)
        override suspend fun syncBatch(operations: List<NetworkSyncOperation>): Result<NetworkSyncBatchResponse> =
            Result.success(NetworkSyncBatchResponse(emptyList()))
        override suspend fun getSnapshot(groupId: String): Result<NetworkGroupSnapshot> =
            Result.failure(UnsupportedOperationException())
        override suspend fun getInviteMetadata(token: String): Result<NetworkInviteMetadata> =
            Result.failure(UnsupportedOperationException())
        override suspend fun submitClaim(groupId: String, memberId: String, claimantDeviceId: String, sasCode: String): Result<NetworkClaimResponse> =
            Result.success(NetworkClaimResponse("PENDING"))
        override suspend fun approveClaim(groupId: String, claimId: String): Result<Boolean> =
            Result.success(true)
    }
}
