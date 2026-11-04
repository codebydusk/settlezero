package app.settlezero.feature.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.GroupRepository
import app.settlezero.core.data.repository.LedgerRepository
import app.settlezero.core.data.repository.MemberRepository
import app.settlezero.core.model.Currency
import app.settlezero.core.model.Group
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GroupItemUiState(
    val group: Group,
    val memberCount: Int = 1,
    val totalSpendingMinor: Long = 0L,
    val userNetBalanceMinor: Long = 0L
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val groups: List<GroupItemUiState> = emptyList(),
    val showCreateDialog: Boolean = false,
    val createGroupName: String = "",
    val createCurrency: Currency = Currency.INR,
    val creatorName: String = "",
    val errorMessage: String? = null
)

class GroupDashboardViewModel(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeGroups()
    }

    private fun observeGroups() {
        viewModelScope.launch {
            groupRepository.observeActiveGroups().collect { groups ->
                val groupUiItems = groups.map { group ->
                    GroupItemUiState(
                        group = group,
                        memberCount = 1,
                        totalSpendingMinor = 0L,
                        userNetBalanceMinor = 0L
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    groups = groupUiItems
                )
            }
        }
    }

    fun onShowCreateDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = show,
            errorMessage = null,
            createGroupName = if (show) _uiState.value.createGroupName else "",
            creatorName = if (show) _uiState.value.creatorName else ""
        )
    }

    fun onGroupNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(createGroupName = name, errorMessage = null)
    }

    fun onCurrencyChanged(currency: Currency) {
        _uiState.value = _uiState.value.copy(createCurrency = currency)
    }

    fun onCreatorNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(creatorName = name, errorMessage = null)
    }

    fun createGroup(onSuccess: (groupId: String) -> Unit) {
        val name = _uiState.value.createGroupName.trim()
        val creator = _uiState.value.creatorName.trim()
        val currency = _uiState.value.createCurrency

        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Group name cannot be blank")
            return
        }
        if (creator.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Your name cannot be blank")
            return
        }

        viewModelScope.launch {
            when (val result = groupRepository.createGroup(name, currency, creator)) {
                is Result.Success -> {
                    val (createdGroup, _) = result.data
                    _uiState.value = _uiState.value.copy(
                        showCreateDialog = false,
                        createGroupName = "",
                        creatorName = "",
                        errorMessage = null
                    )
                    onSuccess(createdGroup.id)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.message ?: "Failed to create group"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }
}
