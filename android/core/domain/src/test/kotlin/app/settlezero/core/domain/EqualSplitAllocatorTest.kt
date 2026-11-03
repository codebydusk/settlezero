package app.settlezero.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EqualSplitAllocatorTest {

    @Test
    fun `exact division splits equally with zero remainder`() {
        val participants = listOf("user-1", "user-2", "user-3")
        val allocations = EqualSplitAllocator.allocate(30000L, participants) // ₹300.00 / 3

        assertThat(allocations).hasSize(3)
        assertThat(allocations.map { it.amountMinor }).containsExactly(10000L, 10000L, 10000L)
        assertThat(allocations.sumOf { it.amountMinor }).isEqualTo(30000L)
    }

    @Test
    fun `remainder minor unit distributed deterministically to first lexicographical member`() {
        // ₹100.00 (10000 paise) split 3 ways: 3334, 3333, 3333
        // Sorted lexicographically: "alice" (3334), "bob" (3333), "charlie" (3333)
        val participants = listOf("charlie", "bob", "alice")
        val allocations = EqualSplitAllocator.allocate(10000L, participants)

        assertThat(allocations[0].memberId).isEqualTo("alice")
        assertThat(allocations[0].amountMinor).isEqualTo(3334L)

        assertThat(allocations[1].memberId).isEqualTo("bob")
        assertThat(allocations[1].amountMinor).isEqualTo(3333L)

        assertThat(allocations[2].memberId).isEqualTo("charlie")
        assertThat(allocations[2].amountMinor).isEqualTo(3333L)

        assertThat(allocations.sumOf { it.amountMinor }).isEqualTo(10000L)
    }

    @Test
    fun `lexicographical stability is independent of input order`() {
        val order1 = listOf("z-id", "a-id", "m-id")
        val order2 = listOf("a-id", "m-id", "z-id")

        val alloc1 = EqualSplitAllocator.allocate(10001L, order1)
        val alloc2 = EqualSplitAllocator.allocate(10001L, order2)

        assertThat(alloc1).isEqualTo(alloc2)
    }

    @Test
    fun `single participant receives full amount`() {
        val allocations = EqualSplitAllocator.allocate(5000L, listOf("solo-member"))
        assertThat(allocations).hasSize(1)
        assertThat(allocations.first().amountMinor).isEqualTo(5000L)
    }

    @Test
    fun `large amount with 7 participants satisfies conservation invariant`() {
        val participants = (1..7).map { "member-$it" }
        val amount = 1_000_000_000_013L // > 1 trillion minor units

        val allocations = EqualSplitAllocator.allocate(amount, participants)
        assertThat(allocations.sumOf { it.amountMinor }).isEqualTo(amount)
    }
}

