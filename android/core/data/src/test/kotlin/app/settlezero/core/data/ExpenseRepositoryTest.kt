package app.settlezero.core.data

import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.OfflineFirstExpenseRepository
import app.settlezero.core.data.repository.OfflineFirstGroupRepository
import app.settlezero.core.data.repository.OfflineFirstMemberRepository
import app.settlezero.core.model.Currency
import app.settlezero.core.model.ExpenseSplitAllocation
import app.settlezero.core.model.Member
import app.settlezero.core.model.SplitType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ExpenseRepositoryTest : BaseRepositoryTest() {

    private lateinit var groupRepo: OfflineFirstGroupRepository
    private lateinit var memberRepo: OfflineFirstMemberRepository
    private lateinit var expenseRepo: OfflineFirstExpenseRepository

    private lateinit var groupId: String
    private lateinit var userA: Member
    private lateinit var userB: Member

    @Before
    override fun setUp() {
        super.setUp()
        groupRepo = OfflineFirstGroupRepository(database, timeProvider)
        memberRepo = OfflineFirstMemberRepository(database, timeProvider)
        expenseRepo = OfflineFirstExpenseRepository(database, timeProvider)

        runTest {
            val (group, creator) = (groupRepo.createGroup("Trip", Currency.INR, "Sayantan") as Result.Success).data
            groupId = group.id
            userA = creator
            userB = (memberRepo.addMember(groupId, "Rahul", userA.id) as Result.Success).data
        }
    }

    @Test
    fun `createExpense commits locally, locks currency, and enqueues sync`() = runTest {
        val splits = listOf(
            ExpenseSplitAllocation(userA.id, 1000L),
            ExpenseSplitAllocation(userB.id, 1000L)
        )

        val result = expenseRepo.createExpense(
            groupId = groupId,
            description = "Lunch",
            amountMinor = 2000L,
            payerMemberId = userA.id,
            createdByMemberId = userA.id,
            expenseDate = 1000L,
            splitType = SplitType.EQUAL,
            allocations = splits
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val expense = (result as Result.Success).data

        // Currency must now be locked
        val group = groupRepo.getGroup(groupId)
        assertThat(group?.isCurrencyLocked).isTrue()

        // Verify active expenses
        val active = expenseRepo.observeActiveExpenses(groupId).first()
        assertThat(active).hasSize(1)
        assertThat(active.first().id).isEqualTo(expense.id)

        // Verify sync queue
        val pending = database.syncQueueDao().getPendingOperations()
        assertThat(pending.any { it.operationType == "CREATE_EXPENSE" }).isTrue()
    }

    @Test
    fun `deleteExpense soft deletes and enqueues sync`() = runTest {
        val splits = listOf(
            ExpenseSplitAllocation(userA.id, 1000L),
            ExpenseSplitAllocation(userB.id, 1000L)
        )

        val expense = (expenseRepo.createExpense(
            groupId = groupId,
            description = "Lunch",
            amountMinor = 2000L,
            payerMemberId = userA.id,
            createdByMemberId = userA.id,
            expenseDate = 1000L,
            splitType = SplitType.EQUAL,
            allocations = splits
        ) as Result.Success).data

        val deleteResult = expenseRepo.deleteExpense(expense.id, deletedByMemberId = userA.id)
        assertThat(deleteResult).isInstanceOf(Result.Success::class.java)

        val active = expenseRepo.observeActiveExpenses(groupId).first()
        val all = expenseRepo.observeExpenses(groupId).first()

        assertThat(active).isEmpty()
        assertThat(all).hasSize(1)
        assertThat(all.first().isDeleted).isTrue()

        val pending = database.syncQueueDao().getPendingOperations()
        assertThat(pending.any { it.operationType == "DELETE_EXPENSE" }).isTrue()
    }
}

