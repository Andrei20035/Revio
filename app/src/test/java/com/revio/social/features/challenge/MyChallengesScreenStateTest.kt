package com.revio.social.features.challenge

import com.revio.social.data.model.Challenge
import com.revio.social.data.model.ChallengeHistoryItem
import com.revio.social.data.model.ChallengeProgress
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.ParticipantState
import com.revio.social.data.model.RewardState
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies [toActiveCardState] carries every field [ChallengeCard] needs — in particular
 * [ChallengeProgress.participantState] and the item's [ChallengeHistoryItem.effectiveStatus],
 * which previously got dropped when converting "ACTIVE NOW" history rows into card state. */
class MyChallengesScreenStateTest {

    private val now = Instant.parse("2026-08-07T12:00:00Z")

    private fun historyItem(
        participantState: ParticipantState,
        effectiveStatus: EffectiveChallengeStatus,
        contributionCount: Int = 3,
        rewardState: RewardState = RewardState.NONE,
    ) = ChallengeHistoryItem(
        challenge = Challenge(
            id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            title = "Weekend Golf Hunt",
            description = null,
            targetFamilyBrand = "Volkswagen",
            targetFamilyName = "Golf",
            requiredPosts = 5,
            rewardPoints = 300,
            startsAt = now.minusSeconds(3600),
            endsAt = now.plusSeconds(3600),
        ),
        effectiveStatus = effectiveStatus,
        progress = ChallengeProgress(
            contributionCount = contributionCount,
            rewardState = rewardState,
            participantState = participantState,
        ),
    )

    @Test
    fun `toActiveCardState propaga participantState`() {
        val item = historyItem(
            participantState = ParticipantState.COMPLETED_PENDING,
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
        )

        val state = item.toActiveCardState(now)

        assertEquals(ParticipantState.COMPLETED_PENDING, state.participantState)
    }

    @Test
    fun `toActiveCardState propaga effectiveStatus`() {
        val item = historyItem(
            participantState = ParticipantState.IN_PROGRESS,
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
        )

        val state = item.toActiveCardState(now)

        assertEquals(EffectiveChallengeStatus.ACTIVE, state.effectiveStatus)
    }

    @Test
    fun `toActiveCardState pastreaza restul campurilor din challenge si progres`() {
        val item = historyItem(
            participantState = ParticipantState.REWARDED,
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            contributionCount = 5,
            rewardState = RewardState.GRANTED,
        )

        val state = item.toActiveCardState(now)

        assertEquals(item.challenge.id, state.challengeId)
        assertEquals("Spot 5 Volkswagen Golf", state.titleLine)
        assertEquals(5, state.contributionCount)
        assertEquals(5, state.requiredPosts)
        assertEquals(300, state.rewardPoints)
        assertEquals(RewardState.GRANTED, state.rewardState)
        assertEquals(false, state.isStale)
    }
}
