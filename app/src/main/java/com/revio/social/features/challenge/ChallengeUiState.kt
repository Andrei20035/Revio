package com.revio.social.features.challenge

import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.ParticipantState
import com.revio.social.data.model.RewardState
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val SECONDS_PER_DAY = 24 * 60 * 60L
private val DAY_THRESHOLD: Duration = Duration.ofHours(24)

/**
 * The challenge countdown's display bucket. Pure over [Instant] durations — the device's
 * timezone never enters this calculation, so changing it has zero effect on the result.
 */
sealed interface RemainingTime {
    /**
     * Strictly more than 24h left. [days] is the *ceiling* of the remaining duration in whole
     * days — e.g. 25h remaining rounds up to 2, never down to 1, since underestimating the time
     * left would mislead the user. Because of the ceiling, [days] never equals 1: anything just
     * over the 24h threshold already rounds up to 2 (see [remainingTimeAt]'s boundary note).
     */
    data class Days(val days: Long) : RemainingTime

    /** 24h or less left (including exactly 24h — see [remainingTimeAt]'s boundary note). */
    data class HoursMinutes(val hours: Long, val minutes: Long) : RemainingTime

    /** [Instant] `endsAt` has been reached or passed. The card has nothing to show. */
    data object Expired : RemainingTime
}

/**
 * Buckets the time left until `endsAt` as of `now`. Pure `Instant` math — never touches
 * `ZoneId.systemDefault()` — so a device timezone change has zero effect on the result.
 *
 * Boundary: strictly more than 24h → [RemainingTime.Days]; 24h or less → [RemainingTime.HoursMinutes].
 * At exactly 24h, remaining is *not* greater than 24h, so it falls into the hours branch as
 * "24h 00m remaining" rather than "1 day remaining" — matching the rule that day counts only
 * ever appear once *more* than 24h remain.
 */
fun remainingTimeAt(now: Instant, endsAt: Instant): RemainingTime {
    val remaining = Duration.between(now, endsAt)
    if (remaining.isZero || remaining.isNegative) return RemainingTime.Expired

    return if (remaining > DAY_THRESHOLD) {
        val wholeSeconds = remaining.seconds + if (remaining.nano > 0) 1 else 0
        val days = (wholeSeconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY
        RemainingTime.Days(days)
    } else {
        val totalMinutes = remaining.toMinutes()
        RemainingTime.HoursMinutes(hours = totalMinutes / 60, minutes = totalMinutes % 60)
    }
}

/**
 * The card's countdown copy, e.g. "2 days remaining" / "5h 08m remaining". Null once expired —
 * there is nothing left to say, and the card disappears rather than showing "0h 00m remaining".
 */
fun RemainingTime.label(): String? = when (this) {
    is RemainingTime.Days -> if (days == 1L) "1 day remaining" else "$days days remaining"
    is RemainingTime.HoursMinutes -> "${hours}h ${minutes.toString().padStart(2, '0')}m remaining"
    RemainingTime.Expired -> null
}

/**
 * The challenge card's UI state. There is no `Loading` case: the design deliberately skips a
 * skeleton (see the plan's §5) — until the first successful load, or when nothing qualifies, the
 * card is simply [Hidden].
 */
sealed interface ChallengeUiState {
    /**
     * Nothing to show: no challenge scheduled, a future or already-ended challenge (filtered out
     * client-side — the server's `/challenges/current` can return the *next* challenge, not only
     * the active one), the initial load hasn't resolved yet, or the initial load failed with no
     * prior data to fall back to.
     */
    data object Hidden : ChallengeUiState

    /** A challenge whose window contains `now` — see `Challenge.isActiveAt`. */
    data class Active(
        val challengeId: UUID,
        /** e.g. "Spot 5 Volkswagen Golf" — composed client-side, never the server's free-text `title`. */
        val titleLine: String,
        val contributionCount: Int,
        val requiredPosts: Int,
        val rewardPoints: Int,
        val rewardState: RewardState,
        /** The server-derived participation state, e.g. distinguishing "threshold reached,
         * reward pending" from plain in-progress. See the plan's §7.2. */
        val participantState: ParticipantState = ParticipantState.UNKNOWN,
        /** The challenge's server-derived lifecycle state (active/scheduled/ended/cancelled) —
         * see the plan's §5/§6. Defaults to [EffectiveChallengeStatus.ACTIVE] for existing
         * call sites that don't set it explicitly. */
        val effectiveStatus: EffectiveChallengeStatus = EffectiveChallengeStatus.ACTIVE,
        val endsAt: Instant,
        val remaining: RemainingTime,
        /** True when the most recent refresh failed and this is stale data from an earlier one. */
        val isStale: Boolean = false,
    ) : ChallengeUiState
}
