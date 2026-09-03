package com.revio.social.features.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.core.network.isNetworkError
import com.revio.social.core.network.onReconnected
import com.revio.social.data.repository.LeaderboardRepository
import com.revio.social.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
    private val userRepository: UserRepository,
    private val connectivity: NetworkConnectivityManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            userRepository.currentUser.filterNotNull().collect { user ->
                _uiState.update { it.copy(navbarAvatarUrl = user.profilePicturePath) }
            }
        }
        viewModelScope.launch {
            connectivity.onReconnected().collect {
                if (_uiState.value.errorMessage != null) load()
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null, isOffline = false) }
            handleResult(isRefresh = true)
        }
    }

    fun retry() {
        if (_uiState.value.isLoading) return
        load()
    }

    /**
     * Called when the screen returns to the foreground (pas 3,
     * docs/plans/avem-un-bug-android-mutable-sky.md) — retries a screen already stuck in a
     * network-error state without depending on the [connectivity] `false -> true` transition
     * this class's own `onReconnected()` collector reacts to, which might never arrive after a
     * stale-cache edge case. Reuses [retry]'s own `isLoading` guard, so this never duplicates a
     * load already in flight.
     */
    fun onResumed() {
        if (_uiState.value.errorMessage != null) retry()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isOffline = false) }
            handleResult(isRefresh = false)
        }
    }

    private suspend fun handleResult(isRefresh: Boolean) {
        when (val result = leaderboardRepository.getLeaderboard()) {
            is ApiResult.Success -> {
                val data = result.data
                val sortedEntries = data.entries.sortedBy { it.rank }
                _uiState.update {
                    it.copy(
                        currentUser = data.currentUser,
                        podium = sortedEntries.take(3),
                        rest = sortedEntries.drop(3),
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null,
                        isOffline = false,
                    )
                }
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = result.message,
                    isOffline = result.isNetworkError,
                )
            }
        }
    }
}
