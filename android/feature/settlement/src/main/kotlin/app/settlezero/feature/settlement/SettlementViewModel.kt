package app.settlezero.feature.settlement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.GroupRepository
import app.settlezero.core.data.repository.LedgerRepository
import app.settlezero.core.data.repository.MemberRepository
import app.settlezero.core.data.repository.SettlementRepository
import app.settlezero.core.model.Currency
import app.settlezero.core.model.CurrencyAmount
import app.settlezero.core.model.DebtTransfer
import app.settlezero.core.model.Group
import app.settlezero.core.model.Member
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

data class SettleUpUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val currency: Currency = Currency.INR,
    val members: List<Member> = emptyList(),
    val simplifiedDebts: List<DebtTransfer> = emptyList(),
    val selectedTransfer: DebtTransfer? = null,
    val amountInput: String = "",
    val note: String = "",
    val showPaymentDialog: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    val maxDebtMinor: Long get() = selectedTransfer?.amountMinor ?: 0L

    val enteredAmountMinor: Long get() {
        if (amountInput.isBlank()) return 0L
        return try {
            val bd = BigDecimal(amountInput.trim())
            if (bd <= BigDecimal.ZERO) 0L
            else bd.movePointRight(currency.scale).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: Exception) {
            0L
        }
    }

    val isAmountValid: Boolean get() {
        val entered = enteredAmountMinor
        return entered > 0L && entered <= maxDebtMinor
    }
}

class SettlementViewModel(
    val groupId: String,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val settlementRepository: SettlementRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettleUpUiState())
    val uiState: StateFlow<SettleUpUiState> = _uiState.asStateFlow()

    init {
        observeDebts()
    }

    private fun observeDebts() {
        viewModelScope.launch {
            combine(
                groupRepository.observeGroup(groupId),
                memberRepository.observeMembers(groupId),
                ledgerRepository.observeSimplifiedDebts(groupId)
            ) { group, members, debts ->
                val curr = group?.let { Currency.findByCode(it.currencyCode) } ?: Currency.INR
                _uiState.value.copy(
                    isLoading = false,
                    group = group,
                    currency = curr,
                    members = members,
                    simplifiedDebts = debts
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun selectTransfer(transfer: DebtTransfer) {
        val curr = _uiState.value.currency
        val initialAmount = if (curr.scale == 0) {
            transfer.amountMinor.toString()
        } else {
            val divisor = BigDecimal.TEN.pow(curr.scale)
            BigDecimal(transfer.amountMinor).divide(divisor, curr.scale, RoundingMode.UNNECESSARY).toPlainString()
        }

        _uiState.value = _uiState.value.copy(
            selectedTransfer = transfer,
            amountInput = initialAmount,
            note = "",
            errorMessage = null,
            showPaymentDialog = true
        )
    }

    fun onAmountChanged(amount: String) {
        _uiState.value = _uiState.value.copy(amountInput = amount, errorMessage = null)
    }

    fun onNoteChanged(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    fun onDismissDialog() {
        _uiState.value = _uiState.value.copy(
            showPaymentDialog = false,
            selectedTransfer = null,
            errorMessage = null
        )
    }

    fun recordSettlement(onSuccess: () -> Unit) {
        val state = _uiState.value
        val transfer = state.selectedTransfer ?: return

        if (!state.isAmountValid) {
            val maxFormatted = CurrencyAmount(transfer.amountMinor).format(state.currency.scale, state.currency.symbol)
            _uiState.value = state.copy(
                errorMessage = "Amount must be between 1 and $maxFormatted (no overpayments)"
            )
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)

        viewModelScope.launch {
            when (val result = settlementRepository.recordSettlement(
                groupId = groupId,
                payerMemberId = transfer.fromMemberId,
                recipientMemberId = transfer.toMemberId,
                amountMinor = state.enteredAmountMinor,
                settlementDate = System.currentTimeMillis(),
                recordedByMemberId = transfer.fromMemberId,
                note = state.note.ifBlank { null }
            )) {
                is Result.Success -> {
                    _uiState.value = state.copy(
                        isSubmitting = false,
                        showPaymentDialog = false,
                        selectedTransfer = null
                    )
                    onSuccess()
                }
                is Result.Error -> {
                    _uiState.value = state.copy(
                        isSubmitting = false,
                        errorMessage = result.message ?: "Failed to record settlement"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }
}

