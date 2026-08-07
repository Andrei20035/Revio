package com.revio.social.features.challenge

import com.revio.social.data.model.ChallengeContribution
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.RewardState
import java.time.Instant
import java.util.UUID

/** The Challenge Detail screen's state — see the plan's §3/§6. */
sealed interface ChallengeDetailUiState {
    data object Loading : ChallengeDetailUiState

    /** A nonexistent, DRAFT, or malformed nav-arg challenge id — `GET /challenges/{id}` 404s. */
    data object NotFound : ChallengeDetailUiState

    data class Error(val message: String) : ChallengeDetailUiState

    /**
     * Covers active/completed/ended/upcoming/revoked — distinguished by [effectiveStatus] and
     * [rewardState], not by separate subtypes (the plan's §6 rejects the boolean-flag explosion
     * that would otherwise be needed here).
     */
    data class Content(
        val challengeId: UUID,
        /** e.g. "Spot 5 Volkswagen Golf" — composed client-side, never the server's free-text `title`. */
        val titleLine: String,
        val description: String?,
        val startsAt: Instant,
        val endsAt: Instant,
        val effectiveStatus: EffectiveChallengeStatus,
        val contributionCount: Int,
        val requiredPosts: Int,
        val rewardPoints: Int,
        val rewardState: RewardState,
        val remaining: RemainingTime,
        val contributions: List<ChallengeContribution>,
        /** True when the most recent refresh failed and this is stale data from an earlier one. */
        val isStale: Boolean = false,
    ) : ChallengeDetailUiState
}
