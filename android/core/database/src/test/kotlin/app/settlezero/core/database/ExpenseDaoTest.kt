package app.settlezero.core.database

import app.settlezero.core.database.model.ExpenseEntity
import app.settlezero.core.database.model.ExpenseSplitEntity
import app.settlezero.core.database.model.GroupEntity
import app.settlezero.core.database.model.MemberEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ExpenseDaoTest : BaseDatabaseTest() {

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

    private val memberA = MemberEntity("mA", "grp-1", "Sayantan", "SUPER_ADMIN", "ACTIVE_CLAIMED", 1000L)
    private val memberB = MemberEntity("mB", "grp-1", "Rahul", "MEMBER", "ACTIVE_CLAIMED", 1000L)

    @Before
    override fun setUp() {
        super.setUp()
        runTest {
            database.groupDao().insertGroup(group)
            database.memberDao().insertMembers(listOf(memberA, memberB))
        }
    }

    @Test
    fun `insertExpenseWithSplits persists expense and relation splits atomically`() = runTest {
        val expense = ExpenseEntity(
            id = "exp-1",
            groupId = "grp-1",
            description = "Lunch",
            amountMinor = 2000L,
            payerMemberId = "mA",
            createdByMemberId = "mA",
            expenseDate = 1000L,
            createdAt = 1000L,
            splitType = "EQUAL"
        )
        val splits = listOf(
            ExpenseSplitEntity("sp-1", "exp-1", "mA", 1000L),
            ExpenseSplitEntity("sp-2", "exp-1", "mB", 1000L)
        )

        database.expenseDao().insertExpenseWithSplits(expense, splits)

        val populated = database.expenseDao().getExpenseById("exp-1")
        assertThat(populated).isNotNull()
        assertThat(populated?.expense?.description).isEqualTo("Lunch")
        assertThat(populated?.splits).hasSize(2)
        assertThat(populated?.splits?.map { it.allocatedAmountMinor }).containsExactly(1000L, 1000L)
    }

    @Test
    fun `active expenses excludes soft deleted expenses`() = runTest {
        val exp1 = ExpenseEntity(
            id = "exp-1",
            groupId = "grp-1",
            description = "Lunch",
            amountMinor = 2000L,
            payerMemberId = "mA",
            createdByMemberId = "mA",
            expenseDate = 1000L,
            createdAt = 1000L,
            splitType = "EQUAL"
        )
        val exp2 = ExpenseEntity(
            id = "exp-2",
            groupId = "grp-1",
            description = "Deleted Dinner",
            amountMinor = 4000L,
            payerMemberId = "mA",
            createdByMemberId = "mA",
            expenseDate = 2000L,
            createdAt = 2000L,
            splitType = "EQUAL",
            isDeleted = true
        )

        database.expenseDao().insertExpense(exp1)
        database.expenseDao().insertExpense(exp2)

        val active = database.expenseDao().observeActiveExpenses("grp-1").first()
        val all = database.expenseDao().observeExpenses("grp-1").first()

        assertThat(active).hasSize(1)
        assertThat(active.first().expense.id).isEqualTo("exp-1")

        assertThat(all).hasSize(2)
    }

    @Test
    fun `soft delete and restore update flags properly`() = runTest {
        val exp = ExpenseEntity(
            id = "exp-1",
            groupId = "grp-1",
            description = "Coffee",
            amountMinor = 300L,
            payerMemberId = "mA",
            createdByMemberId = "mA",
            expenseDate = 1000L,
            createdAt = 1000L,
            splitType = "EQUAL"
        )
        database.expenseDao().insertExpense(exp)

        database.expenseDao().softDeleteExpense("exp-1", deletedByMemberId = "mA", deletedAt = 1500L)

        val deleted = database.expenseDao().getExpenseById("exp-1")
        assertThat(deleted?.expense?.isDeleted).isTrue()
        assertThat(deleted?.expense?.deletedByMemberId).isEqualTo("mA")
        assertThat(deleted?.expense?.deletedAt).isEqualTo(1500L)

        database.expenseDao().restoreExpense("exp-1")
        val restored = database.expenseDao().getExpenseById("exp-1")
        assertThat(restored?.expense?.isDeleted).isFalse()
        assertThat(restored?.expense?.deletedByMemberId).isNull()
        assertThat(restored?.expense?.deletedAt).isNull()
    }

    @Test
    fun `updateExpenseWithSplits atomically replaces old splits with new splits`() = runTest {
        val exp = ExpenseEntity(
            id = "exp-1",
            groupId = "grp-1",
            description = "Snacks",
            amountMinor = 1000L,
            payerMemberId = "mA",
            createdByMemberId = "mA",
            expenseDate = 1000L,
            createdAt = 1000L,
            splitType = "EQUAL"
        )
        val initialSplits = listOf(
            ExpenseSplitEntity("sp-1", "exp-1", "mA", 500L),
            ExpenseSplitEntity("sp-2", "exp-1", "mB", 500L)
        )
        database.expenseDao().insertExpenseWithSplits(exp, initialSplits)

        // Update to Unequal split
        val updatedExp = exp.copy(amountMinor = 1200L, splitType = "UNEQUAL")
        val newSplits = listOf(
            ExpenseSplitEntity("sp-3", "exp-1", "mA", 400L),
            ExpenseSplitEntity("sp-4", "exp-1", "mB", 800L)
        )
        database.expenseDao().updateExpenseWithSplits(updatedExp, newSplits)

        val populated = database.expenseDao().getExpenseById("exp-1")
        assertThat(populated?.expense?.amountMinor).isEqualTo(1200L)
        assertThat(populated?.splits).hasSize(2)
        assertThat(populated?.splits?.map { it.id }).containsExactly("sp-3", "sp-4")
        assertThat(populated?.splits?.map { it.allocatedAmountMinor }).containsExactly(400L, 800L)
    }
}

