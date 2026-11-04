package app.settlezero.feature.claim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.settlezero.core.common.Result
import app.settlezero.core.data.repository.GroupRepository
import app.settlezero.core.data.repository.MemberRepository
import app.settlezero.core.model.Currency
import app.settlezero.core.model.Group
import app.settlezero.core.model.Member
import app.settlezero.core.model.MemberStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.random.Random

enum class JoinMode {
    NEW_MEMBER,
    CLAIM_EXISTING
}

data class ClaimUiState(
    val tokenInput: String = "",
    val isLoading: Boolean = false,
    val group: Group? = null,
    val currency: Currency = Currency.INR,
    val unclaimedMembers: List<Member> = emptyList(),
    val joinMode: JoinMode = JoinMode.NEW_MEMBER,
    val newMemberName: String = "",
    val selectedMemberIdToClaim: String? = null,
    val sasCode: String? = null,
    val isSubmitted: Boolean = false,
    val joinedGroupId: String? = null,
    val errorMessage: String? = null
)

class ClaimViewModel(
    initialToken: String = "",
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClaimUiState(tokenInput = initialToken))
    val uiState: StateFlow<ClaimUiState> = _uiState.asStateFlow()

    init {
        if (initialToken.isNotBlank()) {
            lookupGroup(initialToken)
        }
    }

    fun onTokenChanged(token: String) {
        val cleanToken = extractTokenFromUrl(token)
        _uiState.value = _uiState.value.copy(tokenInput = cleanToken, errorMessage = null)
        if (cleanToken.length >= 8) {
            lookupGroup(cleanToken)
        }
    }

    private fun extractTokenFromUrl(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.contains("/join/")) {
            trimmed.substringAfterLast("/join/").substringBefore("?").substringBefore("/")
        } else {
            trimmed
        }
    }

    fun lookupGroup(token: String? = null) {
        val tokenToLookup = token ?: _uiState.value.tokenInput
        if (tokenToLookup.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter an invite token")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val group = groupRepository.getGroupByInviteToken(tokenToLookup)
            if (group == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    group = null,
                    errorMessage = "Invalid invite token. Check link and try again."
                )
            } else {
                val members = memberRepository.getMembers(group.id)
                val unclaimed = members.filter { it.isUnclaimed }
                val curr = Currency.findByCode(group.currencyCode)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    group = group,
                    currency = curr,
                    unclaimedMembers = unclaimed,
                    errorMessage = null
                )
            }
        }
    }

    fun onJoinModeChanged(mode: JoinMode) {
        _uiState.value = _uiState.value.copy(joinMode = mode, errorMessage = null)
    }

    fun onNewMemberNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(newMemberName = name, errorMessage = null)
    }

    fun onSelectMemberToClaim(memberId: String) {
        val sas = generateSasCode(memberId, _uiState.value.group?.id ?: "")
        _uiState.value = _uiState.value.copy(
            selectedMemberIdToClaim = memberId,
            sasCode = sas,
            errorMessage = null
        )
    }

    private fun generateSasCode(memberId: String, groupId: String): String {
        val input = "$groupId:$memberId"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(input.toByteArray(Charsets.UTF_8))
        // Deterministic 4-digit code from 0000 to 9999
        val num = ((hash[0].toInt() and 0xFF) shl 8 or (hash[1].toInt() and 0xFF)) % 10000
        return String.format("%04d", kotlin.math.abs(num))
    }

    fun submitJoin(onSuccess: (groupId: String) -> Unit) {
        val state = _uiState.value
        val group = state.group ?: return

        when (state.joinMode) {
            JoinMode.NEW_MEMBER -> {
                val name = state.newMemberName.trim()
                if (name.isBlank()) {
                    _uiState.value = state.copy(errorMessage = "Please enter your name")
                    return
                }

                _uiState.value = state.copy(isLoading = true, errorMessage = null)

                viewModelScope.launch {
                    val allMembers = memberRepository.getMembers(group.id)
                    val actorId = allMembers.firstOrNull()?.id ?: ""
                    when (val result = memberRepository.addMember(group.id, name, actorId)) {
                        is Result.Success -> {
                            _uiState.value = state.copy(isLoading = false, joinedGroupId = group.id)
                            onSuccess(group.id)
                        }
                        is Result.Error -> {
                            _uiState.value = state.copy(
                                isLoading = false,
                                errorMessage = result.message ?: "Failed to join group"
                            )
                        }
                        is Result.Loading -> {}
                    }
                }
            }
            JoinMode.CLAIM_EXISTING -> {
                val memberId = state.selectedMemberIdToClaim
                if (memberId == null) {
                    _uiState.value = state.copy(errorMessage = "Please select your name from the unclaimed list")
                    return
                }
                // For existing claim, SAS code is presented for in-person / admin verification
                _uiState.value = state.copy(isSubmitted = true)
            }
        }
    }
}
