package com.revio.app.features.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.app.core.network.ApiResult
import com.revio.app.data.repository.ActivityRepository
import com.revio.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
        load()
        viewModelScope.launch {
            userRepository.currentUser.filterNotNull().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
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

    private fun loadCurrentUser() {
        viewModelScope.launch {
            when (val result = userRepository.getCurrentUser()) {
                is ApiResult.Success -> _uiState.update { it.copy(currentUser = result.data) }
                is ApiResult.Error -> Unit
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            handleResult(isRefresh = false)
        }
    }

    private suspend fun handleResult(isRefresh: Boolean) {
        when (val result = activityRepository.getActivity()) {
            is ApiResult.Success -> {
                val data = result.data
                _uiState.update {
                    it.copy(
                        weeklySpotScore = data.weeklySpotScore,
                        todayInteractions = data.todayInteractions,
                        items = data.items,
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
