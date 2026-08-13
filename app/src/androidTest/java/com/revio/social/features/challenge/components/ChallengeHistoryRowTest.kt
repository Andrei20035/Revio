package com.revio.social.features.challenge.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.data.model.Challenge
import com.revio.social.data.model.ChallengeHistoryItem
import com.revio.social.data.model.ChallengeProgress
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.ParticipantState
import com.revio.social.data.model.RewardState
import java.time.Instant
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers Bloc J5: the new `COMPLETED_PENDING` eyebrow, and the pre-existing rule it must not
 * regress — an ended, not-completed challenge stays a neutral fact ("ENDED"), never an
 * error-styled label. See [ChallengeHistoryRow]'s KDoc. */
@RunWith(AndroidJUnit4::class)
class ChallengeHistoryRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val challenge = Challenge(
        id = UUID.randomUUID(),
        title = "Weekend Golf Hunt",
        description = null,
        targetFamilyBrand = "Volkswagen",
        targetFamilyName = "Golf",
        requiredPosts = 5,
        rewardPoints = 300,
        startsAt = Instant.parse("2026-08-07T00:00:00Z"),
        endsAt = Instant.parse("2026-08-09T00:00:00Z"),
    )

    private fun setRowContent(
        contributionCount: Int,
        rewardState: RewardState,
        participantState: ParticipantState,
        effectiveStatus: EffectiveChallengeStatus,
    ) {
        composeTestRule.setContent {
            ChallengeHistoryRow(
                item = ChallengeHistoryItem(
                    challenge = challenge,
                    effectiveStatus = effectiveStatus,
                    progress = ChallengeProgress(
                        contributionCount = contributionCount,
                        rewardState = rewardState,
                        participantState = participantState,
                    ),
                ),
                onClick = {},
            )
        }
    }

    @Test
    fun `Incheiat, prag neatins - eticheta ramane ENDED, nu devine eroare`() {
        setRowContent(
            contributionCount = 2,
            rewardState = RewardState.NONE,
            participantState = ParticipantState.NOT_COMPLETED,
            effectiveStatus = EffectiveChallengeStatus.ENDED,
        )

        composeTestRule.onNodeWithText("ENDED").assertIsDisplayed()
        composeTestRule.onNodeWithText("REWARD PENDING").assertDoesNotExist()
        composeTestRule.onNodeWithText("REWARD REVOKED").assertDoesNotExist()
        composeTestRule.onNodeWithText("COMPLETED").assertDoesNotExist()
    }

    @Test
    fun `prag atins, finalizare neefectuata - eticheta REWARD PENDING`() {
        setRowContent(
            contributionCount = 5,
            rewardState = RewardState.NONE,
            participantState = ParticipantState.COMPLETED_PENDING,
            effectiveStatus = EffectiveChallengeStatus.ENDED,
        )

        composeTestRule.onNodeWithText("REWARD PENDING").assertIsDisplayed()
        composeTestRule.onNodeWithText("ENDED").assertDoesNotExist()
    }

    @Test
    fun `participantState UNKNOWN de la un server vechi - fara REWARD PENDING, comportament neschimbat`() {
        setRowContent(
            contributionCount = 5,
            rewardState = RewardState.NONE,
            participantState = ParticipantState.UNKNOWN,
            effectiveStatus = EffectiveChallengeStatus.ENDED,
        )

        // Fara participantState de la server, ramane heuristica veche: prag atins + ENDED + NONE = revocat.
        composeTestRule.onNodeWithText("REWARD REVOKED").assertIsDisplayed()
        composeTestRule.onNodeWithText("REWARD PENDING").assertDoesNotExist()
    }
}
