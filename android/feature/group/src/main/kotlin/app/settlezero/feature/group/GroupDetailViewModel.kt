package app.settlezero.feature.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.ExpenseRepository
import app.settlezero.core.data.repository.GroupRepository
import app.settlezero.core.data.repository.LedgerRepository
import app.settlezero.core.data.repository.MemberRepository
import app.settlezero.core.model.DebtTransfer
import app.settlezero.core.model.Expense
import app.settlezero.core.model.Group
import app.settlezero.core.model.Member
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MemberWithBalance(
    val member: Member,
    val netBalanceMinor: Long = 0L
)

data class GroupDetailUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val currentMemberId: String? = null,
    val members: List<MemberWithBalance> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val simplifiedDebts: List<DebtTransfer> = emptyList(),
    val totalSpendingMinor: Long = 0L,
    val myNetBalanceMinor: Long = 0L,
    val showAddMemberDialog: Boolean = false,
    val newMemberName: String = "",
    val errorMessage: String? = null
)

private data class GroupCoreData(
    val group: Group?,
    val members: List<Member>,
    val expenses: List<Expense>,
    val balances: Map<String, Long>,
    val debts: List<DebtTransfer>
)

class GroupDetailViewModel(
    val groupId: String,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val expenseRepository: ExpenseRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupDetailUiState())
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    init {
        observeGroupData()
    }

    private fun observeGroupData() {
        viewModelScope.launch {
            combine(
                groupRepository.observeGroup(groupId),
                memberRepository.observeMembers(groupId),
                expenseRepository.observeActiveExpenses(groupId),
                ledgerRepository.observeGroupBalances(groupId),
                ledgerRepository.observeSimplifiedDebts(groupId)
            ) { group, members, expenses, balances, debts ->
                GroupCoreData(group, members, expenses, balances, debts)
            }.combine(ledgerRepository.observeTotalSpending(groupId)) { core, totalSpending ->
                val currentMember = _uiState.value.currentMemberId?.let { id ->
                    core.members.find { it.id == id }
                } ?: core.members.firstOrNull()

                val membersWithBalance = core.members.map { member ->
                    MemberWithBalance(
                        member = member,
                        netBalanceMinor = core.balances[member.id] ?: 0L
                    )
                }

                val myBalance = currentMember?.let { core.balances[it.id] } ?: 0L

                GroupDetailUiState(
                    isLoading = false,
                    group = core.group,
                    currentMemberId = currentMember?.id,
                    members = membersWithBalance,
                    expenses = core.expenses,
                    simplifiedDebts = core.debts,
                    totalSpendingMinor = totalSpending,
                    myNetBalanceMinor = myBalance,
                    showAddMemberDialog = _uiState.value.showAddMemberDialog,
                    newMemberName = _uiState.value.newMemberName,
                    errorMessage = _uiState.value.errorMessage
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun selectCurrentMember(memberId: String) {
        val balances = _uiState.value.members.associate { it.member.id to it.netBalanceMinor }
        val myBalance = balances[memberId] ?: 0L
        _uiState.value = _uiState.value.copy(
            currentMemberId = memberId,
            myNetBalanceMinor = myBalance
        )
    }

    fun onShowAddMemberDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showAddMemberDialog = show,
            newMemberName = "",
            errorMessage = null
        )
    }

    fun onNewMemberNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(newMemberName = name, errorMessage = null)
    }

    fun addMember() {
        val name = _uiState.value.newMemberName.trim()
        val actorId = _uiState.value.currentMemberId ?: _uiState.value.members.firstOrNull()?.member?.id
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Member name cannot be blank")
            return
        }
        if (actorId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "No actor available")
            return
        }

        viewModelScope.launch {
            when (val result = memberRepository.addMember(groupId, name, actorId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showAddMemberDialog = false,
                        newMemberName = "",
                        errorMessage = null
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.message ?: "Failed to add member"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        val actorId = _uiState.value.currentMemberId ?: _uiState.value.members.firstOrNull()?.member?.id ?: return
        viewModelScope.launch {
            expenseRepository.deleteExpense(expenseId, actorId)
        }
    }

    fun archiveGroup(onSuccess: () -> Unit) {
        val actorId = _uiState.value.currentMemberId ?: _uiState.value.members.firstOrNull()?.member?.id ?: return
        viewModelScope.launch {
            when (groupRepository.archiveGroup(groupId, actorId)) {
                is Result.Success -> onSuccess()
                is Result.Error -> {}
                is Result.Loading -> {}
            }
        }
    }

    fun generateInviteLink(): String {
        val token = _uiState.value.group?.inviteToken ?: ""
        return "https://settlezero.app/join/$token"
    }
}
