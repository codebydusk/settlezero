package app.settlezero.core.data

import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.OfflineFirstGroupRepository
import app.settlezero.core.model.Currency
import app.settlezero.core.model.MemberRole
import app.settlezero.core.model.MemberStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class GroupRepositoryTest : BaseRepositoryTest() {

    private lateinit var repository: OfflineFirstGroupRepository

    @Before
    override fun setUp() {
        super.setUp()
        repository = OfflineFirstGroupRepository(database, timeProvider)
    }

    @Test
    fun `createGroup inserts group and creator and enqueues sync operation`() = runTest {
        val result = repository.createGroup(
            name = "Goa Trip 2026",
            currency = Currency.INR,
            creatorName = "Sayantan"
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val (group, creator) = (result as Result.Success).data

        assertThat(group.name).isEqualTo("Goa Trip 2026")
        assertThat(group.currencyCode).isEqualTo("INR")
        assertThat(group.isCurrencyLocked).isFalse()

        assertThat(creator.name).isEqualTo("Sayantan")
        assertThat(creator.role).isEqualTo(MemberRole.SUPER_ADMIN)
        assertThat(creator.status).isEqualTo(MemberStatus.ACTIVE_CLAIMED)

        // Verify local DB state
        val activeGroups = repository.observeActiveGroups().first()
        assertThat(activeGroups).hasSize(1)
        assertThat(activeGroups.first().id).isEqualTo(group.id)

        // Verify sync queue enqueue
        val pendingOps = database.syncQueueDao().getPendingOperations()
        assertThat(pendingOps).hasSize(1)
        assertThat(pendingOps.first().operationType).isEqualTo("CREATE_GROUP")
        assertThat(pendingOps.first().groupId).isEqualTo(group.id)
    }

    @Test
    fun `archiveGroup updates status and excludes from active groups`() = runTest {
        val (group, creator) = (repository.createGroup("Goa", Currency.INR, "Sayantan") as Result.Success).data

        val archiveResult = repository.archiveGroup(group.id, creator.id)
        assertThat(archiveResult).isInstanceOf(Result.Success::class.java)

        val active = repository.observeActiveGroups().first()
        val archived = repository.observeArchivedGroups().first()

        assertThat(active).isEmpty()
        assertThat(archived).hasSize(1)
        assertThat(archived.first().id).isEqualTo(group.id)
    }
}

