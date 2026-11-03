package app.settlezero.core.database

import app.settlezero.core.database.model.GroupEntity
import app.settlezero.core.database.model.SyncQueueEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SyncQueueDaoTest : BaseDatabaseTest() {

    private val group = GroupEntity(
        id = "grp-1",
        name = "Trip",
        currencyCode = "INR",
        currencyScale = 2,
        status = "ACTIVE",
        inviteToken = "token",
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Before
    override fun setUp() {
        super.setUp()
        runTest {
            database.groupDao().insertGroup(group)
        }
    }

    @Test
    fun `enqueue operations and retrieve pending in FIFO order`() = runTest {
        val op1 = SyncQueueEntity("op-1", "grp-1", "ADD_EXPENSE", "{}", "key-1", "PENDING", null, 0, 1000L)
        val op2 = SyncQueueEntity("op-2", "grp-1", "ADD_SETTLEMENT", "{}", "key-2", "PENDING", null, 0, 2000L)

        database.syncQueueDao().enqueueAll(listOf(op2, op1)) // insert out of order

        val pending = database.syncQueueDao().getPendingOperations()
        assertThat(pending.map { it.id }).containsExactly("op-1", "op-2").inOrder()
    }

    @Test
    fun `status transitions update correctly and reflect in pending count`() = runTest {
        val op = SyncQueueEntity("op-1", "grp-1", "ADD_EXPENSE", "{}", "key-1", "PENDING", null, 0, 1000L)
        database.syncQueueDao().enqueue(op)

        assertThat(database.syncQueueDao().observePendingCount("grp-1").first()).isEqualTo(1)

        // Mark in-flight
        database.syncQueueDao().updateStatus("op-1", "IN_FLIGHT")
        assertThat(database.syncQueueDao().observePendingCount("grp-1").first()).isEqualTo(0)

        // Mark rejected
        database.syncQueueDao().updateStatus("op-1", "REJECTED", errorMessage = "GROUP_ARCHIVED")
        val queue = database.syncQueueDao().observeQueueForGroup("grp-1").first()
        assertThat(queue.first().status).isEqualTo("REJECTED")
        assertThat(queue.first().errorMessage).isEqualTo("GROUP_ARCHIVED")

        // Increment retry attempt
        database.syncQueueDao().incrementAttempt("op-1")
        val queueAfterRetry = database.syncQueueDao().observeQueueForGroup("grp-1").first()
        assertThat(queueAfterRetry.first().attemptCount).isEqualTo(1)

        // Delete operation upon successful sync
        database.syncQueueDao().deleteOperation("op-1")
        assertThat(database.syncQueueDao().observeQueueForGroup("grp-1").first()).isEmpty()
    }
}

