package app.settlezero.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.ExpenseRepository
import app.settlezero.core.data.repository.GroupRepository
import app.settlezero.core.data.repository.MemberRepository
import app.settlezero.core.domain.EqualSplitAllocator
import app.settlezero.core.model.Currency
import app.settlezero.core.model.ExpenseSplitAllocation
import app.settlezero.core.model.Group
import app.settlezero.core.model.Member
import app.settlezero.core.model.MemberStatus
import app.settlezero.core.model.SplitType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

data class AddExpenseUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val currency: Currency = Currency.INR,
    val members: List<Member> = emptyList(),
    val description: String = "",
    val amountInput: String = "",
    val payerMemberId: String? = null,
    val splitType: SplitType = SplitType.EQUAL,
    val selectedParticipantIds: Set<String> = emptySet(),
    val customAmounts: Map<String, String> = emptyMap(),
    val note: String = "",
    val expenseDate: Long = System.currentTimeMillis(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    val totalAmountMinor: Long get() = parseMajorToMinor(amountInput, currency.scale) ?: 0L

    val computedAllocations: List<ExpenseSplitAllocation> get() {
        val total = totalAmountMinor
        if (total <= 0L) return emptyList()

        return when (splitType) {
            SplitType.EQUAL -> {
                if (selectedParticipantIds.isEmpty()) emptyList()
                else EqualSplitAllocator.allocate(total, selectedParticipantIds)
            }
            SplitType.UNEQUAL -> {
                selectedParticipantIds.map { memberId ->
                    val minor = parseMajorToMinor(customAmounts[memberId] ?: "", currency.scale) ?: 0L
                    ExpenseSplitAllocation(memberId, minor)
                }
            }
        }
    }

    val allocatedSumMinor: Long get() = computedAllocations.sumOf { it.amountMinor }

    val remainingMinor: Long get() = totalAmountMinor - allocatedSumMinor

    val isInputValid: Boolean get() {
        if (description.isBlank()) return false
        if (totalAmountMinor <= 0L) return false
        if (payerMemberId.isNullOrBlank()) return false
        if (selectedParticipantIds.isEmpty()) return false
        if (splitType == SplitType.UNEQUAL && remainingMinor != 0L) return false
        return true
    }

    companion object {
        fun parseMajorToMinor(input: String, scale: Int): Long? {
            if (input.isBlank()) return null
            return try {
                val bd = BigDecimal(input.trim())
                if (bd <= BigDecimal.ZERO) null
                else bd.movePointRight(scale).setScale(0, RoundingMode.HALF_UP).longValueExact()
            } catch (e: Exception) {
                null
            }
        }
    }
}

class ExpenseViewModel(
    val groupId: String,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    init {
        loadGroupAndMembers()
    }

    private fun loadGroupAndMembers() {
        viewModelScope.launch {
            combine(
                groupRepository.observeGroup(groupId),
                memberRepository.observeMembers(groupId)
            ) { group, members ->
                val activeMembers = members.filter { it.status != MemberStatus.ARCHIVED }
                val curr = group?.let { Currency.findByCode(it.currencyCode) } ?: Currency.INR
                val defaultPayer = _uiState.value.payerMemberId ?: activeMembers.firstOrNull()?.id
                val defaultParticipants = if (_uiState.value.selectedParticipantIds.isEmpty()) {
                    activeMembers.map { it.id }.toSet()
                } else _uiState.value.selectedParticipantIds

                _uiState.value.copy(
                    isLoading = false,
                    group = group,
                    currency = curr,
                    members = activeMembers,
                    payerMemberId = defaultPayer,
                    selectedParticipantIds = defaultParticipants
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description, errorMessage = null)
    }

    fun onAmountChanged(amount: String) {
        _uiState.value = _uiState.value.copy(amountInput = amount, errorMessage = null)
    }

    fun onPayerSelected(payerMemberId: String) {
        _uiState.value = _uiState.value.copy(payerMemberId = payerMemberId)
    }

    fun onSplitTypeChanged(splitType: SplitType) {
        _uiState.value = _uiState.value.copy(splitType = splitType)
    }

    fun toggleParticipant(memberId: String) {
        val current = _uiState.value.selectedParticipantIds.toMutableSet()
        if (current.contains(memberId)) {
            if (current.size > 1) { // must keep at least 1 participant
                current.remove(memberId)
            }
        } else {
            current.add(memberId)
        }
        _uiState.value = _uiState.value.copy(selectedParticipantIds = current)
    }

    fun onCustomAmountChanged(memberId: String, amount: String) {
        val updated = _uiState.value.customAmounts.toMutableMap()
        updated[memberId] = amount
        _uiState.value = _uiState.value.copy(customAmounts = updated)
    }

    fun onNoteChanged(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    fun submitExpense(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.isInputValid) {
            _uiState.value = state.copy(errorMessage = "Please fill all required fields correctly")
            return
        }

        val payerId = state.payerMemberId ?: return
        val totalMinor = state.totalAmountMinor
        val allocations = state.computedAllocations

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)

        viewModelScope.launch {
            when (val result = expenseRepository.createExpense(
                groupId = groupId,
                description = state.description.trim(),
                amountMinor = totalMinor,
                payerMemberId = payerId,
                createdByMemberId = payerId,
                expenseDate = state.expenseDate,
                splitType = state.splitType,
                allocations = allocations,
                note = state.note.ifBlank { null }
            )) {
                is Result.Success -> {
                    _uiState.value = state.copy(isSubmitting = false)
                    onSuccess()
                }
                is Result.Error -> {
                    _uiState.value = state.copy(
                        isSubmitting = false,
                        errorMessage = result.message ?: "Failed to create expense"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }
}

