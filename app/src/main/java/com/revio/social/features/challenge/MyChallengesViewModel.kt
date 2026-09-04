package com.revio.social.features.challenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.feedback.PostCreationSignal
import com.revio.social.core.feedback.PostRemovalSignal
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.ChallengeSummary
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.MyChallenges
import com.revio.social.data.repository.ChallengeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/**
 * Owns "My Challenges": the summary strip, the active challenge (if any), and the paginated
 * history. Refresh and paging run on separate [Job]s — see the plan's §6 — so a pull-to-refresh
 * never cancels an in-flight "load more", and vice versa. Cursors live only in memory, never in
 * `SavedStateHandle`: a partially-restored paginated list after process death would be more
 * confusing than one that simply reloads from the top.
 */
@HiltViewModel
class MyChallengesViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val postCreationSignal: PostCreationSignal,
    private val postRemovalSignal: PostRemovalSignal,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MyChallengesUiState>(MyChallengesUiState.Loading)
    val uiState: StateFlow<MyChallengesUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var pagingJob: Job? = null

    private var nextCursorEndsAt: Instant? = null
    private var nextCursorId: UUID? = null

    // Guards against PostCreationSignal's replay = 1 — see ChallengeViewModel's identical guard.
    private var lastHandledPostId: UUID? = null

    // Guards against handling the same removal event twice — see MyChallengesEntryViewModel's
    // identical guard on PostRemovalSignal.
    private var lastHandledRemovedPostId: UUID? = null

    init {
        refresh()
        observePostCreation()
        observePostRemoval()
    }

    private fun observePostCreation() {
        viewModelScope.launch {
            postCreationSignal.events.collect { event ->
                if (event.postId != lastHandledPostId) {
                    lastHandledPostId = event.postId
                    refresh()
                }
            }
        }
    }

    /** A post that contributed to a listed challenge may have been deleted by its author or
     * removed by moderation — re-fetch so the summary and per-challenge counts stay authoritative. */
    private fun observePostRemoval() {
        viewModelScope.launch {
            postRemovalSignal.events.collect { event ->
                if (event.postId != lastHandledRemovedPostId) {
                    lastHandledRemovedPostId = event.postId
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            when (val result = challengeRepository.getMyChallenges(limit = PAGE_SIZE)) {
                is ApiResult.Success -> applyFirstPage(result.data)
                is ApiResult.Error -> _uiState.update { current ->
                    if (current is MyChallengesUiState.Content) current else MyChallengesUiState.Error(result.message)
                }
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current !is MyChallengesUiState.Content || !current.hasMore || current.isPaging) return
        val cursorEndsAt = nextCursorEndsAt
        val cursorId = nextCursorId
        if (cursorEndsAt == null || cursorId == null) return

        _uiState.value = current.copy(isPaging = true)

        pagingJob?.cancel()
        pagingJob = viewModelScope.launch {
            when (
                val result = challengeRepository.getMyChallenges(
                    limit = PAGE_SIZE,
                    cursorEndsAt = cursorEndsAt,
                    cursorId = cursorId,
                )
            ) {
                is ApiResult.Success -> {
                    nextCursorEndsAt = result.data.nextCursorEndsAt
                    nextCursorId = result.data.nextCursorId
                    _uiState.update { state ->
                        if (state !is MyChallengesUiState.Content) return@update state
                        state.copy(
                            past = state.past + result.data.challenges,
                            isPaging = false,
                            hasMore = result.data.hasMore,
                        )
                    }
                }

                is ApiResult.Error -> _uiState.update { state ->
                    if (state is MyChallengesUiState.Content) state.copy(isPaging = false) else state
                }
            }
        }
    }

    private fun applyFirstPage(data: MyChallenges) {
        nextCursorEndsAt = data.nextCursorEndsAt
        nextCursorId = data.nextCursorId

        val summary = data.summary ?: ChallengeSummary(joinedCount = 0, completedCount = 0, pointsEarned = 0, totalContributions = 0)
        val active = data.challenges.firstOrNull { it.effectiveStatus == EffectiveChallengeStatus.ACTIVE }
        val past = data.challenges.filter { it != active }

        _uiState.value = if (active == null && past.isEmpty() && summary.joinedCount == 0) {
            MyChallengesUiState.Empty
        } else {
            MyChallengesUiState.Content(
                summary = summary,
                active = active,
                past = past,
                isPaging = false,
                hasMore = data.hasMore,
            )
        }
    }
}
