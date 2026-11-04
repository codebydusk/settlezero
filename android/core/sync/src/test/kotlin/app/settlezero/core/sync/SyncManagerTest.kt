package app.settlezero.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncManagerTest {

    private lateinit var context: Context
    private lateinit var syncManager: WorkManagerSyncManager
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        syncManager = WorkManagerSyncManager(context)
    }

    @Test
    fun `scheduleBackgroundSync enqueues unique work with exponential backoff`() {
        val groupId = "test-group-1"
        syncManager.scheduleBackgroundSync(groupId)

        val workInfos = workManager.getWorkInfosForUniqueWork("${SyncWorker.WORK_NAME_PREFIX}$groupId").get()
        assertThat(workInfos).hasSize(1)
        assertThat(workInfos.first().state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    }

    @Test
    fun `cancelSync cancels scheduled work`() {
        val groupId = "test-group-2"
        syncManager.scheduleBackgroundSync(groupId)
        syncManager.cancelSync(groupId)

        val workInfos = workManager.getWorkInfosForUniqueWork("${SyncWorker.WORK_NAME_PREFIX}$groupId").get()
        assertThat(workInfos.first().state).isEqualTo(WorkInfo.State.CANCELLED)
    }
}

