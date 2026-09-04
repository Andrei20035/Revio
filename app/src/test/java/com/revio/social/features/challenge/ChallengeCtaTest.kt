package com.revio.social.features.challenge

import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.ParticipantState
import com.revio.social.data.model.RewardState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the plan's §3 state matrix for [challengeCta] — one case per row, plus the
 * cross-cutting rules (n > N, stale/inconsistent combinations, old-server UNKNOWN fallback). */
class ChallengeCtaTest {

    private fun cta(
        effectiveStatus: EffectiveChallengeStatus = EffectiveChallengeStatus.ACTIVE,
        participantState: ParticipantState = ParticipantState.IN_PROGRESS,
        rewardState: RewardState = RewardState.NONE,
        contributionCount: Int,
        requiredPosts: Int = 5,
    ) = challengeCta(
        effectiveStatus = effectiveStatus,
        participantState = participantState,
        rewardState = rewardState,
        contributionCount = contributionCount,
        requiredPosts = requiredPosts,
    )

    // Row 1: ACTIVE, REWARDED, GRANTED -> Completed, disabled.
    @Test
    fun `randul 1 - ACTIVE REWARDED GRANTED - Completed dezactivat`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.REWARDED,
            rewardState = RewardState.GRANTED,
            contributionCount = 5,
        )
        assertEquals(ChallengeCta("Completed", enabled = false), result)
    }

    // Row 2: ENDED, REWARDED, GRANTED -> Completed, disabled.
    @Test
    fun `randul 2 - ENDED REWARDED GRANTED - Completed dezactivat`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ENDED,
            participantState = ParticipantState.REWARDED,
            rewardState = RewardState.GRANTED,
            contributionCount = 5,
        )
        assertEquals(ChallengeCta("Completed", enabled = false), result)
    }

    // Row 3: ACTIVE, COMPLETED_PENDING, NONE, n >= N -> Goal reached, disabled.
    @Test
    fun `randul 3 - ACTIVE COMPLETED_PENDING - Goal reached dezactivat`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.COMPLETED_PENDING,
            contributionCount = 5,
        )
        assertEquals(ChallengeCta("Goal reached", enabled = false), result)
    }

    // Row 4: ENDED, COMPLETED_PENDING, NONE, n >= N -> Goal reached, disabled.
    @Test
    fun `randul 4 - ENDED COMPLETED_PENDING - Goal reached dezactivat`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ENDED,
            participantState = ParticipantState.COMPLETED_PENDING,
            contributionCount = 5,
        )
        assertEquals(ChallengeCta("Goal reached", enabled = false), result)
    }

    // Row 5: ACTIVE, IN_PROGRESS, NONE, n = 0 -> Spot now, enabled.
    @Test
    fun `randul 5 - ACTIVE IN_PROGRESS n=0 - Spot now activ`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.IN_PROGRESS,
            contributionCount = 0,
        )
        assertEquals(ChallengeCta("Spot now", enabled = true), result)
    }

    // Row 6: ACTIVE, IN_PROGRESS, NONE, 1 <= n < N -> Spot again, enabled.
    @Test
    fun `randul 6 - ACTIVE IN_PROGRESS n intre 1 si N-1 - Spot again activ`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.IN_PROGRESS,
            contributionCount = 3,
        )
        assertEquals(ChallengeCta("Spot again", enabled = true), result)
    }

    // Row 7: ENDED, NOT_COMPLETED, NONE, n < N -> Challenge ended, disabled.
    @Test
    fun `randul 7 - ENDED NOT_COMPLETED prag neatins - Challenge ended dezactivat`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ENDED,
            participantState = ParticipantState.NOT_COMPLETED,
            contributionCount = 2,
        )
        assertEquals(ChallengeCta("Challenge ended", enabled = false), result)
    }

    // Row 8: ENDED, REVOKED, NONE, n >= N -> Challenge ended, disabled.
    @Test
    fun `randul 8 - ENDED REVOKED prag atins - Challenge ended dezactivat`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ENDED,
            participantState = ParticipantState.REVOKED,
            contributionCount = 5,
        )
        assertEquals(ChallengeCta("Challenge ended", enabled = false), result)
    }

    // Row 9: CANCELLED -> Challenge cancelled, disabled.
    @Test
    fun `randul 9 - CANCELLED - Challenge cancelled dezactivat`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.CANCELLED,
            participantState = ParticipantState.CANCELLED,
            contributionCount = 0,
        )
        assertEquals(ChallengeCta("Challenge cancelled", enabled = false), result)
    }

    // Row 10: SCHEDULED, NOT_STARTED, NONE, n = 0 -> Not started yet, disabled.
    @Test
    fun `randul 10 - SCHEDULED NOT_STARTED - Not started yet dezactivat`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.SCHEDULED,
            participantState = ParticipantState.NOT_STARTED,
            contributionCount = 0,
        )
        assertEquals(ChallengeCta("Not started yet", enabled = false), result)
    }

    // Row 10b: SCHEDULED but reward already GRANTED -> Completed wins (priority rule).
    @Test
    fun `randul 10b - SCHEDULED cu GRANTED - regula de prioritate Completed`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.SCHEDULED,
            participantState = ParticipantState.NOT_STARTED,
            rewardState = RewardState.GRANTED,
            contributionCount = 5,
        )
        assertEquals(ChallengeCta("Completed", enabled = false), result)
    }

    // Row 11: UNKNOWN participantState + GRANTED reward -> Completed (fallback 1).
    @Test
    fun `randul 11 - UNKNOWN cu GRANTED - fallback Completed`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ENDED,
            participantState = ParticipantState.UNKNOWN,
            rewardState = RewardState.GRANTED,
            contributionCount = 5,
        )
        assertEquals(ChallengeCta("Completed", enabled = false), result)
    }

    // Row 12: ACTIVE, UNKNOWN, NONE, n >= N -> Goal reached (fallback 2, count-vs-threshold).
    @Test
    fun `randul 12 - ACTIVE UNKNOWN prag atins - fallback Goal reached`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.UNKNOWN,
            contributionCount = 5,
        )
        assertEquals(ChallengeCta("Goal reached", enabled = false), result)
    }

    // Row 13: ACTIVE, UNKNOWN, NONE, n < N -> Spot now/Spot again (fallback 3).
    @Test
    fun `randul 13 - ACTIVE UNKNOWN n=0 - fallback Spot now`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.UNKNOWN,
            contributionCount = 0,
        )
        assertEquals(ChallengeCta("Spot now", enabled = true), result)
    }

    @Test
    fun `randul 13 - ACTIVE UNKNOWN n peste 0 - fallback Spot again`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.UNKNOWN,
            contributionCount = 2,
        )
        assertEquals(ChallengeCta("Spot again", enabled = true), result)
    }

    // Row 14: ENDED, UNKNOWN, NONE -> safe fallback, Challenge ended, disabled.
    @Test
    fun `randul 14 - ENDED UNKNOWN - fallback sigur Challenge ended`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ENDED,
            participantState = ParticipantState.UNKNOWN,
            contributionCount = 2,
        )
        assertEquals(ChallengeCta("Challenge ended", enabled = false), result)
    }

    // n > N (threshold overshoot) behaves exactly like n == N.
    @Test
    fun `n peste N - se comporta identic cu n egal N - Goal reached`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.COMPLETED_PENDING,
            contributionCount = 7,
            requiredPosts = 5,
        )
        assertEquals(ChallengeCta("Goal reached", enabled = false), result)
    }

    @Test
    fun `n peste N cu UNKNOWN - fallback tot Goal reached`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.UNKNOWN,
            contributionCount = 7,
            requiredPosts = 5,
        )
        assertEquals(ChallengeCta("Goal reached", enabled = false), result)
    }

    // Inconsistent combination: participantState says REWARDED but rewardState says NONE ->
    // participantState wins (per the plan's §3 tie-break rule).
    @Test
    fun `inconsistenta - REWARDED cu rewardState NONE - participantState castiga - Completed`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ENDED,
            participantState = ParticipantState.REWARDED,
            rewardState = RewardState.NONE,
            contributionCount = 5,
        )
        assertEquals(ChallengeCta("Completed", enabled = false), result)
    }

    // Inconsistent combination: COMPLETED_PENDING participantState but rewardState already
    // GRANTED -> GRANTED wins (Completed has top priority regardless of participantState).
    @Test
    fun `inconsistenta - COMPLETED_PENDING cu rewardState GRANTED - Completed castiga`() {
        val result = cta(
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.COMPLETED_PENDING,
            rewardState = RewardState.GRANTED,
            contributionCount = 5,
        )
        assertEquals(ChallengeCta("Completed", enabled = false), result)
    }
}
