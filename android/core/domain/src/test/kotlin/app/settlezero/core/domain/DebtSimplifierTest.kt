package app.settlezero.core.domain

import app.settlezero.core.model.Expense
import app.settlezero.core.model.ExpenseSplitAllocation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DebtSimplifierTest {

    @Test
    fun `two members with simple balance generates single transfer`() {
        // Rahul (-300), Sayantan (+300) -> Rahul pays Sayantan 300
        val balances = mapOf(
            "rahul" to -30000L,
            "sayantan" to 30000L
        )

        val transfers = DebtSimplifier.simplify(balances)

        assertThat(transfers).hasSize(1)
        assertThat(transfers.first().fromMemberId).isEqualTo("rahul")
        assertThat(transfers.first().toMemberId).isEqualTo("sayantan")
        assertThat(transfers.first().amountMinor).isEqualTo(30000L)
    }

    @Test
    fun `cyclic debt resolves to zero transfers when net balances are zero`() {
        // A owes B 100, B owes C 100, C owes A 100
        // Net balances are all 0!
        val balances = mapOf(
            "alice" to 0L,
            "bob" to 0L,
            "charlie" to 0L
        )

        val transfers = DebtSimplifier.simplify(balances)
        assertThat(transfers).isEmpty()
    }

    @Test
    fun `four-person debt reduces to minimum transfers`() {
        // Balances:
        // A: -500
        // B: -200
        // C: +400
        // D: +300
        // Total negative: 700, total positive: 700
        val balances = mapOf(
            "alice" to -50000L,
            "bob" to -20000L,
            "charlie" to 40000L,
            "david" to 30000L
        )

        val transfers = DebtSimplifier.simplify(balances)

        // Sum of all transfers must equal total debt cleared
        val totalTransferred = transfers.sumOf { it.amountMinor }
        assertThat(totalTransferred).isEqualTo(70000L)

        // Verify each person's net effect from transfers:
        val netFromTransfers = mutableMapOf<String, Long>()
        for (t in transfers) {
            netFromTransfers[t.fromMemberId] = (netFromTransfers[t.fromMemberId] ?: 0L) - t.amountMinor
            netFromTransfers[t.toMemberId] = (netFromTransfers[t.toMemberId] ?: 0L) + t.amountMinor
        }

        assertThat(netFromTransfers["alice"]).isEqualTo(-50000L)
        assertThat(netFromTransfers["bob"]).isEqualTo(-20000L)
        assertThat(netFromTransfers["charlie"]).isEqualTo(40000L)
        assertThat(netFromTransfers["david"]).isEqualTo(30000L)
    }

    @Test
    fun `tie breaking is completely deterministic regardless of map order`() {
        // Two debtors with identical debt: "user-b" (-100), "user-a" (-100)
        // One creditor: "user-c" (+200)
        // Sorting debtor by amount (100 vs 100), tie broken by ID ascending ("user-a" first, then "user-b")
        val balances = mapOf(
            "user-b" to -10000L,
            "user-a" to -10000L,
            "user-c" to 20000L
        )

        val transfers = DebtSimplifier.simplify(balances)

        assertThat(transfers).hasSize(2)
        assertThat(transfers[0].fromMemberId).isEqualTo("user-a")
        assertThat(transfers[0].toMemberId).isEqualTo("user-c")
        assertThat(transfers[0].amountMinor).isEqualTo(10000L)

        assertThat(transfers[1].fromMemberId).isEqualTo("user-b")
        assertThat(transfers[1].toMemberId).isEqualTo("user-c")
        assertThat(transfers[1].amountMinor).isEqualTo(10000L)
    }

    @Test
    fun `direct debts calculation produces unsimplified pairwise ledger`() {
        // Expense 1: A pays 300 for A, B, C (B owes A 100, C owes A 100)
        // Expense 2: B pays 150 for B, C (C owes B 75)
        val exp1 = Expense(
            id = "e1",
            groupId = "g1",
            description = "Lunch",
            amountMinor = 30000L,
            payerMemberId = "a",
            createdByMemberId = "a",
            expenseDate = 1000L,
            createdAt = 1000L,
            allocations = listOf(
                ExpenseSplitAllocation("a", 10000L),
                ExpenseSplitAllocation("b", 10000L),
                ExpenseSplitAllocation("c", 10000L)
            )
        )
        val exp2 = Expense(
            id = "e2",
            groupId = "g1",
            description = "Snacks",
            amountMinor = 15000L,
            payerMemberId = "b",
            createdByMemberId = "b",
            expenseDate = 2000L,
            createdAt = 2000L,
            allocations = listOf(
                ExpenseSplitAllocation("b", 7500L),
                ExpenseSplitAllocation("c", 7500L)
            )
        )

        val direct = DebtSimplifier.calculateDirectDebts(
            listOf("a", "b", "c"),
            listOf(exp1, exp2),
            emptyList()
        )

        // B owes A 10000
        // C owes A 10000
        // C owes B 7500
        assertThat(direct).hasSize(3)
        assertThat(direct.find { it.fromMemberId == "b" && it.toMemberId == "a" }?.amountMinor).isEqualTo(10000L)
        assertThat(direct.find { it.fromMemberId == "c" && it.toMemberId == "a" }?.amountMinor).isEqualTo(10000L)
        assertThat(direct.find { it.fromMemberId == "c" && it.toMemberId == "b" }?.amountMinor).isEqualTo(7500L)
    }
}

