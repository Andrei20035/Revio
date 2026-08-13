package com.revio.social.data.model

import java.time.Instant
import java.util.UUID

/**
 * Domain model for a weekend challenge's public config. Mirrors the server's `ChallengeDTO` —
 * `title` is free text an admin wrote and does not itself contain [requiredPosts]/[targetFamilyBrand]/
 * [targetFamilyName]; UI that needs the "Spot 5 Volkswagen Golf" phrasing composes it from those
 * fields rather than trusting [title]'s format.
 */
data class Challenge(
    val id: UUID,
    val title: String,
    val description: String?,
    val targetFamilyBrand: String,
    val targetFamilyName: String,
    val requiredPosts: Int,
    val rewardPoints: Int,
    val startsAt: Instant,
    val endsAt: Instant,
)

/** Whether [now] falls inside this challenge's window — the client-side "is it active" check. */
fun Challenge.isActiveAt(now: Instant): Boolean = !now.isBefore(startsAt) && now.isBefore(endsAt)

enum class RewardState {
    NONE,
    GRANTED,

    /** Any value the server sends that this client doesn't recognize yet. */
    UNKNOWN,
}

/** The viewer's own progress on one challenge. */
data class ChallengeProgress(
    val contributionCount: Int,
    val rewardState: RewardState,
    val participantState: ParticipantState = ParticipantState.UNKNOWN,
)

/** The server-derived participation state for the caller on one challenge — see the plan's §7.2. */
enum class ParticipantState {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED_PENDING,
    REWARDED,
    NOT_COMPLETED,
    REVOKED,
    CANCELLED,

    /** Any value the server sends that this client doesn't recognize yet, or a missing field
     * from an older server that doesn't send [participantState] at all. */
    UNKNOWN,
}

/** Response shape of `GET /challenges/current`. Both fields are null when nothing is scheduled. */
data class CurrentChallenge(
    val challenge: Challenge?,
    val progress: ChallengeProgress?,
)

/** One post that counted toward a challenge, oldest first per `GET /challenges/{id}/progress`. */
data class ChallengeContribution(
    val postId: UUID,
    val createdAt: Instant,
    val imageUrl: String? = null,
    val carBrand: String? = null,
    val carModel: String? = null,
)

/** Response shape of `GET /challenges/{id}/progress`. */
data class ChallengeProgressDetail(
    val progress: ChallengeProgress,
    val contributions: List<ChallengeContribution>,
)

/** The lifecycle state the server computed for a challenge at request time — see the plan's §5. */
enum class EffectiveChallengeStatus {
    ACTIVE,
    ENDED,
    SCHEDULED,
    CANCELLED,

    /** Any value the server sends that this client doesn't recognize yet. */
    UNKNOWN,
}

/** The caller's lifetime challenge participation — the summary strip in "My Challenges". */
data class ChallengeSummary(
    val joinedCount: Int,
    val completedCount: Int,
    val pointsEarned: Int,
    val totalContributions: Int,
)

/** One row of `GET /challenges/me`: a challenge the caller participated in, plus their progress on it. */
data class ChallengeHistoryItem(
    val challenge: Challenge,
    val effectiveStatus: EffectiveChallengeStatus,
    val progress: ChallengeProgress,
)

/**
 * Response shape of `GET /challenges/me`. [summary] is present only on the first page (no
 * cursor given).
 */
data class MyChallenges(
    val summary: ChallengeSummary?,
    val challenges: List<ChallengeHistoryItem>,
    val hasMore: Boolean,
    val nextCursorEndsAt: Instant?,
    val nextCursorId: UUID?,
)
