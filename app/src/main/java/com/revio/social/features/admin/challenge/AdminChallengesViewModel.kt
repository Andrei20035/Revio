package com.revio.social.features.admin.challenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.isNetworkError
import com.revio.social.data.model.AdminChallengePage
import com.revio.social.data.repository.AdminChallengeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/** Owns the admin "Challenges" dashboard: initial load, retry, and keyset-paginated "load more". */
@HiltViewModel
class AdminChallengesViewModel @Inject constructor(
    private val adminChallengeRepository: AdminChallengeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminChallengesUiState())
    val uiState: StateFlow<AdminChallengesUiState> = _uiState.asStateFlow()

    private var nextCursorCreatedAt: Instant? = null
    private var nextCursorId: UUID? = null

    init {
        load()
    }

    fun load() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isOffline = false) }
            when (val result = adminChallengeRepository.listChallenges(limit = PAGE_SIZE)) {
                is ApiResult.Success -> applyFirstPage(result.data)
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message, isOffline = result.isNetworkError)
                }
            }
        }
    }

    fun retry() {
        if (_uiState.value.isLoading) return
        load()
    }

    fun loadMore() {
        val current = _uiState.value
        if (!current.hasMore || current.isPaging || current.isLoading) return
        val cursorCreatedAt = nextCursorCreatedAt
        val cursorId = nextCursorId
        if (cursorCreatedAt == null || cursorId == null) return

        _uiState.update { it.copy(isPaging = true) }

        viewModelScope.launch {
            when (
                val result = adminChallengeRepository.listChallenges(
                    limit = PAGE_SIZE,
                    cursorCreatedAt = cursorCreatedAt,
                    cursorId = cursorId,
                )
            ) {
                is ApiResult.Success -> {
                    nextCursorCreatedAt = result.data.nextCursor?.lastCreatedAt
                    nextCursorId = result.data.nextCursor?.lastChallengeId
                    _uiState.update { state ->
                        state.copy(
                            challenges = state.challenges + result.data.challenges,
                            isPaging = false,
                            hasMore = result.data.hasMore,
                        )
                    }
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(isPaging = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun applyFirstPage(data: AdminChallengePage) {
        nextCursorCreatedAt = data.nextCursor?.lastCreatedAt
        nextCursorId = data.nextCursor?.lastChallengeId
        _uiState.update {
            it.copy(
                challenges = data.challenges,
                isLoading = false,
                errorMessage = null,
                isOffline = false,
                hasMore = data.hasMore,
            )
        }
    }
}
