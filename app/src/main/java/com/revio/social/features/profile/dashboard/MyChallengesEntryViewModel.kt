package com.revio.social.features.profile.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.feedback.PostCreationSignal
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.MyChallenges
import com.revio.social.data.repository.ChallengeRepository
import com.revio.social.features.challenge.RemainingTime
import com.revio.social.features.challenge.minuteTicker
import com.revio.social.features.challenge.remainingTimeAt
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "My Challenges" entry card's state — see the plan's §3/§6 for the three content variants. */
sealed interface MyChallengesEntryUiState {
    /** Shimmer on the two text lines; the chevron stays — the card is navigable regardless. */
    data object Loading : MyChallengesEntryUiState

    /** No participation at all: never seen this feature in action yet. */
    data object Empty : MyChallengesEntryUiState

    /** A challenge whose window contains `now`. */
    data class Active(
        val challengeId: UUID,
        /** e.g. "Spot 5 Volkswagen Golf" — composed client-side, never the server's free-text `title`. */
        val titleLine: String,
        val contributionCount: Int,
        val requiredPosts: Int,
        val endsAt: Instant,
        val remaining: RemainingTime,
    ) : MyChallengesEntryUiState

    /** No active challenge right now, but the caller has history worth summarizing. */
    data class Summary(
        val joinedCount: Int,
        val completedCount: Int,
        val pointsEarned: Int,
    ) : MyChallengesEntryUiState
}

/**
 * Owns the Profile card's data, independent of [com.revio.social.features.challenge.ChallengeViewModel] —
 * that one is coupled to `/challenges/current` and *hides itself* when nothing is active, which is
 * exactly the opposite of what the always-visible Profile entry point needs (see the plan's §6).
 * Fetches `/challenges/me` with `limit = 1`. The card never surfaces its own error state: a failed
 * refresh keeps whatever was last shown, or falls back to [MyChallengesEntryUiState.Empty] if
 * nothing has loaded yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyChallengesEntryViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val clock: Clock,
    private val postCreationSignal: PostCreationSignal,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MyChallengesEntryUiState>(MyChallengesEntryUiState.Loading)
    val uiState: StateFlow<MyChallengesEntryUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    // Guards against PostCreationSignal's replay = 1 — see ChallengeViewModel's identical guard.
    private var lastHandledPostId: UUID? = null

    init {
        refresh()
        observeCountdown()
        observePostCreation()
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

    /** Mirrors [com.revio.social.features.challenge.ChallengeViewModel.observeCountdown]. */
    private fun observeCountdown() {
        viewModelScope.launch {
            uiState
                .map { state -> (state as? MyChallengesEntryUiState.Active)?.let { it.challengeId to it.endsAt } }
                .distinctUntilChanged()
                .flatMapLatest { identity ->
                    if (identity == null) emptyFlow() else minuteTicker(clock).map { now -> remainingTimeAt(now, identity.second) }
                }
                .distinctUntilChanged()
                .collect { remaining -> applyRemaining(remaining) }
        }
    }

    /** On expiry, a fresh `/challenges/me` fetch is the source of truth for what replaces the
     * active card (summary or empty) — the countdown itself never invents that fallback locally. */
    private fun applyRemaining(remaining: RemainingTime) {
        _uiState.update { current ->
            if (current !is MyChallengesEntryUiState.Active) return@update current
            if (remaining is RemainingTime.Expired) {
                refresh()
                current
            } else {
                current.copy(remaining = remaining)
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            when (val result = challengeRepository.getMyChallenges(limit = 1)) {
                is ApiResult.Success -> applyData(result.data)
                is ApiResult.Error -> {
                    if (_uiState.value is MyChallengesEntryUiState.Loading) {
                        _uiState.value = MyChallengesEntryUiState.Empty
                    }
                    // Otherwise: leave the current (last-known-good) state exactly as it is.
                }
            }
        }
    }

    private fun applyData(data: MyChallenges) {
        val active = data.challenges.firstOrNull { it.effectiveStatus == EffectiveChallengeStatus.ACTIVE }
        val summary = data.summary

        _uiState.value = when {
            active != null -> MyChallengesEntryUiState.Active(
                challengeId = active.challenge.id,
                titleLine = "Spot ${active.challenge.requiredPosts} ${active.challenge.targetFamilyBrand} ${active.challenge.targetFamilyName}",
                contributionCount = active.progress.contributionCount,
                requiredPosts = active.challenge.requiredPosts,
                endsAt = active.challenge.endsAt,
                remaining = remainingTimeAt(clock.instant(), active.challenge.endsAt),
            )

            summary != null && summary.joinedCount > 0 -> MyChallengesEntryUiState.Summary(
                joinedCount = summary.joinedCount,
                completedCount = summary.completedCount,
                pointsEarned = summary.pointsEarned,
            )

            else -> MyChallengesEntryUiState.Empty
        }
    }
}
