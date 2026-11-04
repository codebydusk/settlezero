package app.settlezero.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.DataPortabilityRepository
import app.settlezero.core.data.repository.GroupExportData
import app.settlezero.core.data.repository.GroupRepository
import app.settlezero.core.data.repository.HistoryRepository
import app.settlezero.core.data.repository.MemberRepository
import app.settlezero.core.model.ActivityHistory
import app.settlezero.core.model.Group
import app.settlezero.core.model.Member
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class HistoryFilter {
    ALL,
    EXPENSES,
    SETTLEMENTS,
    MEMBERS
}

data class HistoryUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val historyItems: List<ActivityHistory> = emptyList(),
    val selectedFilter: HistoryFilter = HistoryFilter.ALL,
    val isExporting: Boolean = false,
    val exportJsonString: String? = null,
    val showExportDialog: Boolean = false,
    val importPreview: GroupExportData? = null,
    val showImportDialog: Boolean = false,
    val errorMessage: String? = null
) {
    val filteredHistory: List<ActivityHistory> get() {
        return when (selectedFilter) {
            HistoryFilter.ALL -> historyItems
            HistoryFilter.EXPENSES -> historyItems.filter {
                it.eventType.contains("EXPENSE")
            }
            HistoryFilter.SETTLEMENTS -> historyItems.filter {
                it.eventType.contains("SETTLEMENT")
            }
            HistoryFilter.MEMBERS -> historyItems.filter {
                it.eventType.contains("MEMBER")
            }
        }
    }
}

class HistoryViewModel(
    val groupId: String,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val historyRepository: HistoryRepository,
    private val dataPortabilityRepository: DataPortabilityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                groupRepository.observeGroup(groupId),
                memberRepository.observeMembers(groupId),
                historyRepository.observeHistory(groupId)
            ) { group, members, history ->
                _uiState.value.copy(
                    isLoading = false,
                    group = group,
                    members = members,
                    historyItems = history
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun onFilterSelected(filter: HistoryFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun exportGroup() {
        _uiState.value = _uiState.value.copy(isExporting = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = dataPortabilityRepository.exportGroup(groupId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportJsonString = result.data,
                        showExportDialog = true
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = result.message ?: "Failed to export group data"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun dismissExportDialog() {
        _uiState.value = _uiState.value.copy(showExportDialog = false, exportJsonString = null)
    }

    fun onImportJsonSubmitted(jsonString: String) {
        viewModelScope.launch {
            when (val result = dataPortabilityRepository.parseGroup(jsonString)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        importPreview = result.data,
                        showImportDialog = true,
                        errorMessage = null
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Invalid JSON backup file: ${result.message}"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun cloneImportedGroup(customName: String?, onSuccess: (newGroupId: String) -> Unit) {
        val preview = _uiState.value.importPreview ?: return
        viewModelScope.launch {
            when (val result = dataPortabilityRepository.cloneGroup(preview, customName)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showImportDialog = false,
                        importPreview = null
                    )
                    onSuccess(result.data.id)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.message ?: "Failed to clone group"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun dismissImportDialog() {
        _uiState.value = _uiState.value.copy(showImportDialog = false, importPreview = null)
    }
}

