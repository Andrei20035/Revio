package com.revio.social.features.challenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.feedback.PostCreationSignal
import com.revio.social.core.feedback.PostRemovalSignal
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.Challenge
import com.revio.social.data.model.ChallengeProgress
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.ParticipantState
import com.revio.social.data.model.RewardState
import com.revio.social.data.repository.ChallengeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
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

/** What caused a [ChallengeViewModel.refresh] call. Only [PullToRefresh] bypasses the
 * coalescing window — it's the one trigger the user explicitly asked for. */
enum class ChallengeRefreshTrigger {
    Initial,
    PullToRefresh,
    Other,
}

private val MIN_REFRESH_INTERVAL: Duration = Duration.ofSeconds(5)

/**
 * Ev. — pas 5.10: `/challenges/current` outcome. Measurement only, no UI surface — the card's own
 * KDoc is explicit that it never shows its own error state, so a failure here stays invisible to
 * the user (Categoria 4-ish: silent by design), but an aggregate failure rate is still worth
 * knowing about.
 */
private const val EVENT_CHALLENGE_LOAD_RESULT = "challenge_load_result"

/**
 * Owns the weekend-challenge card's data, independent of [com.revio.social.features.feed.FeedViewModel] —
 * an error or slow response on `/challenges/current` must never affect the feed itself. See the
 * plan's §4: no Room, server is the authority, the card is allowed to be absent or stale but never
 * shows its own error state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChallengeViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val clock: Clock,
    private val postCreationSignal: PostCreationSignal,
    private val postRemovalSignal: PostRemovalSignal,
    private val analyticsClient: AnalyticsClient? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChallengeUiState>(ChallengeUiState.Hidden)
    val uiState: StateFlow<ChallengeUiState> = _uiState.asStateFlow()

    private var challengeLoadJob: Job? = null
    private var lastSuccessfulLoadAt: Instant? = null

    // Guards against PostCreationSignal's replay = 1: a freshly-subscribing collector always
    // gets the last-ever event redelivered, however old. This dedupes that redelivery (and any
    // other repeat of the same postId) rather than treating it as a brand-new upload.
    private var lastHandledPostId: UUID? = null

    // Guards against handling the same removal event twice — see MyChallengesEntryViewModel's
    // identical guard on PostRemovalSignal.
    private var lastHandledRemovedPostId: UUID? = null

    init {
        refresh(ChallengeRefreshTrigger.Initial)
        observeCountdown()
        observePostCreation()
        observePostRemoval()
    }

    /**
     * A newly confirmed post *might* have contributed to the active challenge (only camera posts
     * with a car model do — see `PostService.kt` — but [com.revio.social.core.feedback.PostCreatedEvent]
     * doesn't carry the source, so a gallery upload also triggers this refresh; the plan's §7
     * accepts that extra GET rather than widening a signal shared with `FirstPostFeedbackController`).
     * A failed refresh here is invisible to the uploader: it only ever marks the card stale.
     */
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

    /**
     * A post that contributed to the active challenge may have been deleted by its author or
     * removed by moderation. `PullToRefresh` bypasses the coalescing window here — a removal
     * within 5s of a prior successful load would otherwise be silently swallowed, leaving the
     * card showing a contribution count the server no longer agrees with.
     */
    private fun observePostRemoval() {
        viewModelScope.launch {
            postRemovalSignal.events.collect { event ->
                if (event.postId != lastHandledRemovedPostId) {
                    lastHandledRemovedPostId = event.postId
                    refresh(ChallengeRefreshTrigger.PullToRefresh)
                }
            }
        }
    }

    /**
     * Keeps [ChallengeUiState.Active.remaining] current while a challenge is active, and hides
     * the card locally the moment `endsAt` passes — no network call involved. Gated on the
     * challenge's identity (id + endsAt), not on [uiState] itself, so the ticker's own writes
     * (which only touch `remaining`/`isStale`) don't restart it via [flatMapLatest]; only a
     * genuine transition (a different challenge, or in/out of Hidden) does.
     */
    private fun observeCountdown() {
        viewModelScope.launch {
            uiState
                .map { state -> (state as? ChallengeUiState.Active)?.let { it.challengeId to it.endsAt } }
                .distinctUntilChanged()
                .flatMapLatest { identity ->
                    if (identity == null) emptyFlow() else minuteTicker(clock).map { now -> remainingTimeAt(now, identity.second) }
                }
                // Caps updates to real bucket changes — e.g. many minute-ticks in a row can share
                // the same "N days remaining" bucket, and each of those is skipped here.
                .distinctUntilChanged()
                .collect { remaining -> applyRemaining(remaining) }
        }
    }

    private fun applyRemaining(remaining: RemainingTime) {
        _uiState.update { current ->
            if (current !is ChallengeUiState.Active) return@update current
            if (remaining is RemainingTime.Expired) ChallengeUiState.Hidden else current.copy(remaining = remaining)
        }
    }

    /**
     * Call when the screen returns to the foreground. Recomputes `remaining` immediately from the
     * current clock — the app may have sat in the background past a minute boundary, or past
     * `endsAt` entirely — then kicks a [refresh], still subject to the coalescing window.
     */
    fun onResumed() {
        val current = _uiState.value
        if (current is ChallengeUiState.Active) {
            applyRemaining(remainingTimeAt(clock.instant(), current.endsAt))
        }
        refresh()
    }

    fun refresh(trigger: ChallengeRefreshTrigger = ChallengeRefreshTrigger.Other) {
        val last = lastSuccessfulLoadAt
        if (trigger != ChallengeRefreshTrigger.PullToRefresh &&
            last != null &&
            Duration.between(last, clock.instant()) < MIN_REFRESH_INTERVAL
        ) {
            return
        }

        challengeLoadJob?.cancel()
        challengeLoadJob = viewModelScope.launch {
            val result = challengeRepository.getCurrentChallenge()
            analyticsClient?.log(
                AnalyticsEvent(
                    name = EVENT_CHALLENGE_LOAD_RESULT,
                    params = buildMap {
                        put("outcome", AnalyticsParamValue.StringValue(if (result is ApiResult.Success) "success" else "failure"))
                        if (result is ApiResult.Error) {
                            put("failure_code", AnalyticsParamValue.StringValue(result.code ?: "unknown"))
                        }
                    },
                )
            )
            when (result) {
                is ApiResult.Success -> {
                    lastSuccessfulLoadAt = clock.instant()
                    _uiState.value = toUiState(result.data.challenge, result.data.progress)
                }

                is ApiResult.Error -> {
                    // An optional card never surfaces its own error: keep showing the last known
                    // Active state (marked stale), or stay Hidden if there was nothing to show yet.
                    _uiState.update { current ->
                        if (current is ChallengeUiState.Active) current.copy(isStale = true) else ChallengeUiState.Hidden
                    }
                }
            }
        }
    }

    private fun toUiState(challenge: Challenge?, progress: ChallengeProgress?): ChallengeUiState {
        val now = clock.instant()
        // A challenge that hasn't started yet is still shown — only a truly ended one (`now` at
        // or past `endsAt`) hides the card. `/challenges/current` can return the *next* scheduled
        // challenge, not only the currently active one, and that upcoming state is now surfaced
        // instead of being filtered out client-side.
        if (challenge == null || !now.isBefore(challenge.endsAt)) return ChallengeUiState.Hidden

        val effectiveStatus = if (now.isBefore(challenge.startsAt)) {
            EffectiveChallengeStatus.SCHEDULED
        } else {
            EffectiveChallengeStatus.ACTIVE
        }

        return ChallengeUiState.Active(
            challengeId = challenge.id,
            titleLine = "Spot ${challenge.requiredPosts} ${challenge.targetFamilyBrand} ${challenge.targetFamilyName}",
            contributionCount = progress?.contributionCount ?: 0,
            requiredPosts = challenge.requiredPosts,
            rewardPoints = challenge.rewardPoints,
            rewardState = progress?.rewardState ?: RewardState.NONE,
            participantState = progress?.participantState ?: ParticipantState.UNKNOWN,
            effectiveStatus = effectiveStatus,
            endsAt = challenge.endsAt,
            remaining = remainingTimeAt(now, challenge.endsAt),
            isStale = false,
        )
    }
}
