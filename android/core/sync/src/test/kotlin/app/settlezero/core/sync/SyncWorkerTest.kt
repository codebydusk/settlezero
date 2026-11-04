package app.settlezero.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.settlezero.core.database.SettleZeroDatabase
import app.settlezero.core.database.model.GroupEntity
import app.settlezero.core.database.model.SyncQueueEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    @Test
    fun `syncWorker processes and clears pending queue operations`() = runTest {
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

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(database.syncQueueDao().getPendingOperations()).isEmpty()
    }
}

