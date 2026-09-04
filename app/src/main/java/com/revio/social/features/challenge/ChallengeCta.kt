package com.revio.social.features.challenge

import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.ParticipantState
import com.revio.social.data.model.RewardState

private const val CTA_SPOT_NOW = "Spot now"
private const val CTA_SPOT_AGAIN = "Spot again"
private const val CTA_GOAL_REACHED = "Goal reached"
private const val CTA_COMPLETED = "Completed"
private const val CTA_CHALLENGE_ENDED = "Challenge ended"
private const val CTA_CHALLENGE_CANCELLED = "Challenge cancelled"
private const val CTA_NOT_STARTED_YET = "Not started yet"

/**
 * The challenge CTA's single source of truth: what the button says and whether it can be
 * pressed. Shared by the Feed card, "ACTIVE NOW" in My Challenges, and the Challenge Detail
 * screen so all three surfaces render identically for the same inputs — see the plan's §3/§5.
 */
data class ChallengeCta(val label: String, val enabled: Boolean)

/**
 * Derives [ChallengeCta] from the challenge's lifecycle and the caller's own progress. Pure and
 * exhaustive — see the plan's §3 state matrix for the full rationale, including the fallback
 * behavior for an older server that doesn't yet send [participantState] (`UNKNOWN`).
 *
 * Evaluation order (first match wins):
 * 1. The reward has been granted → always `Completed`, regardless of lifecycle status — a
 *    challenge that ended with the reward already granted still reads "Completed", never
 *    "Challenge ended".
 * 2. The post threshold was reached but the reward is still pending → `Goal reached`.
 * 3. Still in progress (or an old server's `UNKNOWN` participant state, using the count-vs-
 *    threshold fallback) → `Spot now` for the first post, `Spot again` after that — both enabled.
 * 4. Cancelled → `Challenge cancelled`.
 * 5. Not started yet → `Not started yet`.
 * 6. Anything else (ended without reaching the threshold, or an unrecognized/inconsistent
 *    combination) → `Challenge ended`, disabled — the safe fallback.
 */
fun challengeCta(
    effectiveStatus: EffectiveChallengeStatus,
    participantState: ParticipantState,
    rewardState: RewardState,
    contributionCount: Int,
    requiredPosts: Int,
): ChallengeCta {
    val isGranted = rewardState == RewardState.GRANTED || participantState == ParticipantState.REWARDED
    if (isGranted) return ChallengeCta(CTA_COMPLETED, enabled = false)

    val thresholdReached = contributionCount >= requiredPosts
    val isCompletedPending = participantState == ParticipantState.COMPLETED_PENDING ||
        (participantState == ParticipantState.UNKNOWN && thresholdReached)
    if (isCompletedPending) return ChallengeCta(CTA_GOAL_REACHED, enabled = false)

    val isInProgress = participantState == ParticipantState.IN_PROGRESS ||
        (participantState == ParticipantState.UNKNOWN && !thresholdReached)
    if (isInProgress && effectiveStatus == EffectiveChallengeStatus.ACTIVE) {
        val label = if (contributionCount <= 0) CTA_SPOT_NOW else CTA_SPOT_AGAIN
        return ChallengeCta(label, enabled = true)
    }

    if (participantState == ParticipantState.CANCELLED || effectiveStatus == EffectiveChallengeStatus.CANCELLED) {
        return ChallengeCta(CTA_CHALLENGE_CANCELLED, enabled = false)
    }

    if (participantState == ParticipantState.NOT_STARTED || effectiveStatus == EffectiveChallengeStatus.SCHEDULED) {
        return ChallengeCta(CTA_NOT_STARTED_YET, enabled = false)
    }

    return ChallengeCta(CTA_CHALLENGE_ENDED, enabled = false)
}

/**
 * The three flags [ChallengeHistoryRow][com.revio.social.features.challenge.components.ChallengeHistoryRow]
 * derives for one row of "My Challenges"' history list — kept here, alongside [challengeCta], as
 * the one place this exact combination is computed (see the plan's §6 pas 6).
 *
 * Deliberately **not** the same derivation as [challengeCta]: this does not apply the
 * UNKNOWN-participantState-plus-threshold fallback, and it distinguishes a revoked reward from a
 * merely-pending one. An older server that hasn't sent [participantState] yet must keep showing
 * the row's pre-existing behavior unchanged — see `ChallengeHistoryRowTest`'s explicit regression
 * case for participantState == UNKNOWN.
 */
data class ChallengeHistoryStatus(
    val isGranted: Boolean,
    val isRevoked: Boolean,
    val isCompletedPending: Boolean,
)

fun challengeHistoryStatus(
    effectiveStatus: EffectiveChallengeStatus,
    participantState: ParticipantState,
    rewardState: RewardState,
    contributionCount: Int,
    requiredPosts: Int,
): ChallengeHistoryStatus {
    val isGranted = rewardState == RewardState.GRANTED
    val isRevoked = !isGranted && rewardState == RewardState.NONE &&
        effectiveStatus == EffectiveChallengeStatus.ENDED && contributionCount >= requiredPosts
    // Threshold reached but the finalization job hasn't granted (or revoked) the reward yet —
    // the server is the authority here (see the plan's §7.2); an older server that doesn't send
    // participantState at all just never shows this branch and falls through to the status below.
    val isCompletedPending = !isGranted && !isRevoked && participantState == ParticipantState.COMPLETED_PENDING
    return ChallengeHistoryStatus(isGranted = isGranted, isRevoked = isRevoked, isCompletedPending = isCompletedPending)
}
