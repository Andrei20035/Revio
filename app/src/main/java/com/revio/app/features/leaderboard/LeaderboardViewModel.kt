package com.revio.app.features.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.app.core.network.ApiResult
import com.revio.app.data.repository.LeaderboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            handleResult(isRefresh = true)
        }
    }

    fun retry() {
        if (_uiState.value.isLoading) return
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
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
                    )
                }
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = result.message,
                )
            }
        }
    }
}
