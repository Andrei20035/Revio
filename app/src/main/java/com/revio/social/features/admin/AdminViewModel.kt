package com.revio.social.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.isNetworkError
import com.revio.social.data.model.ModerationReason
import com.revio.social.data.remote.dto.admin.ModerationDecision
import com.revio.social.data.remote.dto.admin.ReportStatus
import com.revio.social.data.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Backs every admin-panel screen (AdminReportsScreen, AdminUserScreen) plus the
 * "Remove post (admin)" action reachable from Feed/SeePostOverlay. Each screen — and each
 * caller of the remove-post action — gets its own instance via `hiltViewModel()`; the three
 * areas of state below are independent of each other.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    // ---------- Reports queue ----------

    private val _reportsState = MutableStateFlow(AdminReportsUiState())
    val reportsState: StateFlow<AdminReportsUiState> = _reportsState.asStateFlow()

    fun loadReports() {
        if (_reportsState.value.isLoading) return
        viewModelScope.launch {
            _reportsState.update { it.copy(isLoading = true, errorMessage = null, isOffline = false) }
            fetchReports()
        }
    }

    fun refreshReports() {
        if (_reportsState.value.isRefreshing) return
        viewModelScope.launch {
            _reportsState.update { it.copy(isRefreshing = true, errorMessage = null, isOffline = false) }
            fetchReports()
        }
    }

    fun retryReports() {
        if (_reportsState.value.isLoading) return
        loadReports()
    }

    private suspend fun fetchReports() {
        when (val result = adminRepository.listReports(ReportStatus.PENDING)) {
            is ApiResult.Success -> _reportsState.update {
                it.copy(
                    reports = result.data,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                    isOffline = false,
                )
            }
            is ApiResult.Error -> _reportsState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = result.message,
                    isOffline = result.isNetworkError,
                )
            }
        }
    }

    fun resolveReport(id: UUID, decision: ModerationDecision) {
        if (_reportsState.value.resolvingReportId != null) return
        viewModelScope.launch {
            _reportsState.update { it.copy(resolvingReportId = id, errorMessage = null) }
            when (val result = adminRepository.resolveReport(id, decision)) {
                is ApiResult.Success -> _reportsState.update { state ->
                    state.copy(
                        reports = state.reports.filterNot { it.id == id },
                        resolvingReportId = null,
                    )
                }
                is ApiResult.Error -> _reportsState.update {
                    it.copy(resolvingReportId = null, errorMessage = result.message)
                }
            }
        }
    }

    // ---------- User search / moderation detail ----------

    private val _userState = MutableStateFlow(AdminUserUiState())
    val userState: StateFlow<AdminUserUiState> = _userState.asStateFlow()

    fun onQueryChanged(query: String) {
        _userState.update { it.copy(query = query) }
    }

    fun searchUsers() {
        val query = _userState.value.query.trim()
        if (query.isEmpty() || _userState.value.isSearching) return
        viewModelScope.launch {
            _userState.update { it.copy(isSearching = true, searchError = null) }
            when (val result = adminRepository.searchUsers(query)) {
                is ApiResult.Success -> _userState.update {
                    it.copy(searchResults = result.data, isSearching = false, searchError = null)
                }
                is ApiResult.Error -> _userState.update {
                    it.copy(isSearching = false, searchError = result.message)
                }
            }
        }
    }

    fun selectUser(userId: UUID) {
        _userState.update { it.copy(selectedUserId = userId, detail = null, detailError = null) }
        loadUserModeration(userId)
    }

    fun backToSearch() {
        _userState.update { it.copy(selectedUserId = null, detail = null, detailError = null) }
    }

    fun retryUserModeration() {
        val userId = _userState.value.selectedUserId ?: return
        loadUserModeration(userId)
    }

    private fun loadUserModeration(userId: UUID) {
        viewModelScope.launch {
            _userState.update { it.copy(isLoadingDetail = true, detailError = null) }
            when (val result = adminRepository.getUserModeration(userId)) {
                is ApiResult.Success -> _userState.update {
                    if (it.selectedUserId == userId) {
                        it.copy(detail = result.data, isLoadingDetail = false, detailError = null)
                    } else it
                }
                is ApiResult.Error -> _userState.update {
                    if (it.selectedUserId == userId) {
                        it.copy(isLoadingDetail = false, detailError = result.message)
                    } else it
                }
            }
        }
    }

    fun ban(durationDays: Int?, permanent: Boolean, reason: String?) {
        val userId = _userState.value.selectedUserId ?: return
        if (_userState.value.isMutating) return
        viewModelScope.launch {
            _userState.update { it.copy(isMutating = true, mutationError = null) }
            when (val result = adminRepository.banUser(userId, durationDays, permanent, reason)) {
                is ApiResult.Success -> {
                    _userState.update { it.copy(isMutating = false) }
                    loadUserModeration(userId)
                }
                is ApiResult.Error -> _userState.update { it.copy(isMutating = false, mutationError = result.message) }
            }
        }
    }

    fun unban() {
        val userId = _userState.value.selectedUserId ?: return
        if (_userState.value.isMutating) return
        viewModelScope.launch {
            _userState.update { it.copy(isMutating = true, mutationError = null) }
            when (val result = adminRepository.unbanUser(userId)) {
                is ApiResult.Success -> {
                    _userState.update { it.copy(isMutating = false) }
                    loadUserModeration(userId)
                }
                is ApiResult.Error -> _userState.update { it.copy(isMutating = false, mutationError = result.message) }
            }
        }
    }

    fun revokeViolation(violationId: UUID) {
        val userId = _userState.value.selectedUserId ?: return
        if (_userState.value.isMutating) return
        viewModelScope.launch {
            _userState.update { it.copy(isMutating = true, mutationError = null) }
            when (val result = adminRepository.revokeViolation(violationId)) {
                is ApiResult.Success -> {
                    _userState.update { it.copy(isMutating = false) }
                    loadUserModeration(userId)
                }
                is ApiResult.Error -> _userState.update { it.copy(isMutating = false, mutationError = result.message) }
            }
        }
    }

    fun consumeMutationError() {
        _userState.update { it.copy(mutationError = null) }
    }

    // ---------- Post removal (Feed / SeePostOverlay's "Remove post (admin)") ----------

    private val _removePostState = MutableStateFlow(AdminRemovePostUiState())
    val removePostState: StateFlow<AdminRemovePostUiState> = _removePostState.asStateFlow()

    fun removePost(postId: UUID, reason: ModerationReason, reasonDetails: String?, onSuccess: () -> Unit) {
        if (_removePostState.value.isSubmitting) return
        viewModelScope.launch {
            _removePostState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = adminRepository.removePost(postId, reason, reasonDetails)) {
                is ApiResult.Success -> {
                    _removePostState.update { AdminRemovePostUiState() }
                    onSuccess()
                }
                is ApiResult.Error -> _removePostState.update {
                    it.copy(isSubmitting = false, errorMessage = result.message)
                }
            }
        }
    }

    fun consumeRemovePostError() {
        _removePostState.update { it.copy(errorMessage = null) }
    }
}
