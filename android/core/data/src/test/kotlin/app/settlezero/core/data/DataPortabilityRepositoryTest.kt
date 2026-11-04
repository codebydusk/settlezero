package app.settlezero.core.data

import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.OfflineFirstDataPortabilityRepository
import app.settlezero.core.data.repository.OfflineFirstExpenseRepository
import app.settlezero.core.data.repository.OfflineFirstGroupRepository
import app.settlezero.core.data.repository.OfflineFirstHistoryRepository
import app.settlezero.core.data.repository.OfflineFirstMemberRepository
import app.settlezero.core.data.repository.OfflineFirstSettlementRepository
import app.settlezero.core.model.Currency
import app.settlezero.core.model.ExpenseSplitAllocation
import app.settlezero.core.model.SplitType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DataPortabilityRepositoryTest : BaseRepositoryTest() {

    private lateinit var groupRepo: OfflineFirstGroupRepository
    private lateinit var memberRepo: OfflineFirstMemberRepository
    private lateinit var expenseRepo: OfflineFirstExpenseRepository
    private lateinit var settlementRepo: OfflineFirstSettlementRepository
    private lateinit var historyRepo: OfflineFirstHistoryRepository
    private lateinit var portabilityRepo: OfflineFirstDataPortabilityRepository

    override fun setUp() {
        super.setUp()
        groupRepo = OfflineFirstGroupRepository(database, timeProvider)
        memberRepo = OfflineFirstMemberRepository(database, timeProvider)
        expenseRepo = OfflineFirstExpenseRepository(database, timeProvider)
        settlementRepo = OfflineFirstSettlementRepository(database, timeProvider)
        historyRepo = OfflineFirstHistoryRepository(database)
        portabilityRepo = OfflineFirstDataPortabilityRepository(database, timeProvider)
    }

    @Test
    fun exportAndParseGroupRoundTripPreservesAllEntities() = runTest {
        // Create group and members
        val groupResult = groupRepo.createGroup("Goa Trip", Currency.INR, "Sayantan")
        assertThat(groupResult).isInstanceOf(Result.Success::class.java)
        val (group, creator) = (groupResult as Result.Success).data

        val memberBResult = memberRepo.addMember(group.id, "Rahul", creator.id)
        val memberB = (memberBResult as Result.Success).data

        // Add expense
        val allocations = listOf(
            ExpenseSplitAllocation(creator.id, 50000L),
            ExpenseSplitAllocation(memberB.id, 50000L)
        )
        expenseRepo.createExpense(
            groupId = group.id,
            description = "Resort stay",
            amountMinor = 100000L,
            payerMemberId = creator.id,
            createdByMemberId = creator.id,
            expenseDate = 1000L,
            splitType = SplitType.EQUAL,
            allocations = allocations
        )

        // Record settlement
        settlementRepo.recordSettlement(
            groupId = group.id,
            payerMemberId = memberB.id,
            recipientMemberId = creator.id,
            amountMinor = 50000L,
            settlementDate = 2000L,
            recordedByMemberId = memberB.id
        )

        // Verify history captured events
        val history = historyRepo.observeHistory(group.id).first()
        assertThat(history.size).isAtLeast(4) // Group created, Member added, Expense created, Settlement recorded

        // Export Group
        val exportResult = portabilityRepo.exportGroup(group.id)
        assertThat(exportResult).isInstanceOf(Result.Success::class.java)
        val jsonString = (exportResult as Result.Success).data

        assertThat(jsonString).contains("\"version\": \"1.0\"")
        assertThat(jsonString).contains("Goa Trip")
        assertThat(jsonString).contains("Resort stay")
        assertThat(jsonString).contains("100000")

        // Parse Group
        val parseResult = portabilityRepo.parseGroup(jsonString)
        assertThat(parseResult).isInstanceOf(Result.Success::class.java)
        val parsed = (parseResult as Result.Success).data

        assertThat(parsed.group.name).isEqualTo("Goa Trip")
        assertThat(parsed.members).hasSize(2)
        assertThat(parsed.expenses).hasSize(1)
        assertThat(parsed.settlements).hasSize(1)
    }

    @Test
    fun cloneImportedGroupGeneratesFreshIdsAndNewEditableTimeline() = runTest {
        // Setup initial group
        val groupResult = groupRepo.createGroup("Trip 1", Currency.USD, "Alice")
        val (group, creator) = (groupResult as Result.Success).data
        val bob = (memberRepo.addMember(group.id, "Bob", creator.id) as Result.Success).data

        expenseRepo.createExpense(
            groupId = group.id,
            description = "Lunch",
            amountMinor = 4000L,
            payerMemberId = creator.id,
            createdByMemberId = creator.id,
            expenseDate = 1000L,
            splitType = SplitType.EQUAL,
            allocations = listOf(
                ExpenseSplitAllocation(creator.id, 2000L),
                ExpenseSplitAllocation(bob.id, 2000L)
            )
        )

        val jsonString = (portabilityRepo.exportGroup(group.id) as Result.Success).data
        val exportData = (portabilityRepo.parseGroup(jsonString) as Result.Success).data

        // Clone Group
        val cloneResult = portabilityRepo.cloneGroup(exportData, "Trip 1 Cloned")
        assertThat(cloneResult).isInstanceOf(Result.Success::class.java)
        val clonedGroup = (cloneResult as Result.Success).data

        assertThat(clonedGroup.id).isNotEqualTo(group.id)
        assertThat(clonedGroup.name).isEqualTo("Trip 1 Cloned")
        assertThat(clonedGroup.inviteToken).isNotEqualTo(group.inviteToken)

        // Verify cloned members have new IDs
        val clonedMembers = memberRepo.getMembers(clonedGroup.id)
        assertThat(clonedMembers).hasSize(2)
        assertThat(clonedMembers.map { it.id }).doesNotContain(creator.id)
        assertThat(clonedMembers.map { it.id }).doesNotContain(bob.id)

        // Verify cloned expenses belong to cloned group and have updated participant IDs
        val clonedExpenses = expenseRepo.getActiveExpenses(clonedGroup.id)
        assertThat(clonedExpenses).hasSize(1)
        val clonedExpense = clonedExpenses[0]
        assertThat(clonedExpense.groupId).isEqualTo(clonedGroup.id)
        assertThat(clonedExpense.amountMinor).isEqualTo(4000L)
        assertThat(clonedExpense.allocations.map { it.memberId }).containsExactlyElementsIn(clonedMembers.map { it.id })
    }
}

