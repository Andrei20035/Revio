package com.revio.social.features.challenge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.feedback.PostCreationSignal
import com.revio.social.core.navigation.Screen
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.Challenge
import com.revio.social.data.model.ChallengeProgressDetail
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.repository.ChallengeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The exact string the server sends for a 404 on `GET /challenges/{id}` (both the DRAFT and
 * nonexistent cases — see `ChallengeRoutes.kt`). Matching on it is the only way to distinguish
 * "not found" from any other failure without widening [ApiResult.Error]'s shape, which is a
 * shared contract well beyond this screen's scope.
 */
private const val CHALLENGE_NOT_FOUND_MESSAGE = "Challenge not found"

/**
 * Owns the Challenge Detail screen's data. `challengeId` comes from the nav arg — see the plan's
 * §6. Config (`GET /challenges/{id}`) and progress (`GET /challenges/{id}/progress`) are fetched
 * in parallel; either failing with the server's "not found" message means the whole screen is
 * [ChallengeDetailUiState.NotFound].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChallengeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val challengeRepository: ChallengeRepository,
    private val clock: Clock,
    private val postCreationSignal: PostCreationSignal,
) : ViewModel() {

    private val challengeId: UUID? = savedStateHandle.get<String>(Screen.ChallengeDetail.ARG_CHALLENGE_ID)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private val _uiState = MutableStateFlow<ChallengeDetailUiState>(ChallengeDetailUiState.Loading)
    val uiState: StateFlow<ChallengeDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    // Guards against PostCreationSignal's replay = 1 — see ChallengeViewModel's identical guard.
    private var lastHandledPostId: UUID? = null

    init {
        val id = challengeId
        if (id == null) {
            _uiState.value = ChallengeDetailUiState.NotFound
        } else {
            refresh()
            observeCountdown()
            observePostCreation()
        }
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

    /** Mirrors [ChallengeViewModel.observeCountdown] — see its KDoc for the gating rationale. */
    private fun observeCountdown() {
        viewModelScope.launch {
            uiState
                .map { state -> (state as? ChallengeDetailUiState.Content)?.let { it.challengeId to it.endsAt } }
                .distinctUntilChanged()
                .flatMapLatest { identity ->
                    if (identity == null) emptyFlow() else minuteTicker(clock).map { now -> remainingTimeAt(now, identity.second) }
                }
                .distinctUntilChanged()
                .collect { remaining -> applyRemaining(remaining) }
        }
    }

    /**
     * Unlike the Feed card, expiry never hides the screen: it transitions [Content.effectiveStatus]
     * to [EffectiveChallengeStatus.ENDED] locally (hiding the CTA immediately) and kicks a refresh
     * for the server's final word on the reward — closing the screen under the user's thumb would
     * be hostile (see the plan's §6/§9).
     */
    private fun applyRemaining(remaining: RemainingTime) {
        _uiState.update { current ->
            if (current !is ChallengeDetailUiState.Content) return@update current
            if (remaining is RemainingTime.Expired && current.effectiveStatus == EffectiveChallengeStatus.ACTIVE) {
                refresh()
                current.copy(remaining = remaining, effectiveStatus = EffectiveChallengeStatus.ENDED)
            } else {
                current.copy(remaining = remaining)
            }
        }
    }

    /** Call when the screen returns to the foreground — mirrors `ChallengeViewModel.onResumed`. */
    fun onResumed() {
        val current = _uiState.value
        if (current is ChallengeDetailUiState.Content) {
            applyRemaining(remainingTimeAt(clock.instant(), current.endsAt))
        }
        refresh()
    }

    fun refresh() {
        val id = challengeId ?: return

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val configDeferred = async { challengeRepository.getChallenge(id) }
            val progressDeferred = async { challengeRepository.getChallengeProgress(id) }
            val configResult = configDeferred.await()
            val progressResult = progressDeferred.await()

            when {
                configResult is ApiResult.Error && configResult.message == CHALLENGE_NOT_FOUND_MESSAGE ->
                    _uiState.value = ChallengeDetailUiState.NotFound

                configResult is ApiResult.Error -> handleError(configResult.message)

                progressResult is ApiResult.Error -> handleError(progressResult.message)

                configResult is ApiResult.Success && progressResult is ApiResult.Success ->
                    _uiState.value = toContent(configResult.data, progressResult.data)
            }
        }
    }

    private fun handleError(message: String) {
        _uiState.update { current ->
            if (current is ChallengeDetailUiState.Content) current.copy(isStale = true) else ChallengeDetailUiState.Error(message)
        }
    }

    private fun toContent(challenge: Challenge, progressDetail: ChallengeProgressDetail): ChallengeDetailUiState.Content {
        val now = clock.instant()
        val effectiveStatus = when {
            now.isBefore(challenge.startsAt) -> EffectiveChallengeStatus.SCHEDULED
            now.isBefore(challenge.endsAt) -> EffectiveChallengeStatus.ACTIVE
            else -> EffectiveChallengeStatus.ENDED
        }

        return ChallengeDetailUiState.Content(
            challengeId = challenge.id,
            titleLine = "Spot ${challenge.requiredPosts} ${challenge.targetFamilyBrand} ${challenge.targetFamilyName}",
            description = challenge.description,
            startsAt = challenge.startsAt,
            endsAt = challenge.endsAt,
            effectiveStatus = effectiveStatus,
            contributionCount = progressDetail.progress.contributionCount,
            requiredPosts = challenge.requiredPosts,
            rewardPoints = challenge.rewardPoints,
            rewardState = progressDetail.progress.rewardState,
            remaining = remainingTimeAt(now, challenge.endsAt),
            contributions = progressDetail.contributions,
            isStale = false,
        )
    }
}
