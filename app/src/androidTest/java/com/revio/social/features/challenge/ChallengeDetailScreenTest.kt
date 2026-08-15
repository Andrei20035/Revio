package com.revio.social.features.challenge

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.RewardState
import java.time.Instant
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the plan's §5.4 "Completed" checkmark badge on the challenge detail screen: pending
 * (threshold reached, reward not granted yet) and rewarded (granted) must render distinct second
 * lines — see Bloc J4.
 */
@RunWith(AndroidJUnit4::class)
class ChallengeDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun contentState(
        contributionCount: Int = 5,
        requiredPosts: Int = 5,
        rewardPoints: Int = 300,
        rewardState: RewardState = RewardState.NONE,
        effectiveStatus: EffectiveChallengeStatus = EffectiveChallengeStatus.ACTIVE,
    ) = ChallengeDetailUiState.Content(
        challengeId = UUID.randomUUID(),
        titleLine = "Spot 5 Volkswagen Golf",
        description = null,
        startsAt = Instant.parse("2026-08-07T00:00:00Z"),
        endsAt = Instant.parse("2026-08-09T00:00:00Z"),
        effectiveStatus = effectiveStatus,
        contributionCount = contributionCount,
        requiredPosts = requiredPosts,
        rewardPoints = rewardPoints,
        rewardState = rewardState,
        remaining = RemainingTime.Days(1),
        contributions = emptyList(),
    )

    @Test
    fun `prag_atins_recompensa_neacordata_-_Completed_cu_linia_de_asteptare`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 5,
                    requiredPosts = 5,
                    rewardState = RewardState.NONE,
                    effectiveStatus = EffectiveChallengeStatus.ACTIVE,
                ),
                onSpotNow = {},
            )
        }

        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
        composeTestRule.onNodeWithText("✓").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reward pending until challenge ends").assertIsDisplayed()
    }

    @Test
    fun `prag_atins_recompensa_acordata_-_Completed_cu_linia_de_puncte_primite`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 5,
                    requiredPosts = 5,
                    rewardState = RewardState.GRANTED,
                    effectiveStatus = EffectiveChallengeStatus.ENDED,
                ),
                onSpotNow = {},
            )
        }

        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
        composeTestRule.onNodeWithText("✓").assertIsDisplayed()
        // "+300 pts earned" apare de doua ori: bifa noua si cardul REWARD existent, ambele corecte.
        composeTestRule.onAllNodesWithText("+300 pts earned").assertCountEquals(2)
        composeTestRule.onNodeWithText("Reward pending until challenge ends").assertDoesNotExist()
    }

    @Test
    fun `prag_neatins_-_fara_bifa_Completed`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 3,
                    requiredPosts = 5,
                    rewardState = RewardState.NONE,
                    effectiveStatus = EffectiveChallengeStatus.ACTIVE,
                ),
                onSpotNow = {},
            )
        }

        composeTestRule.onNodeWithText("Completed").assertDoesNotExist()
        composeTestRule.onNodeWithText("Reward pending until challenge ends").assertDoesNotExist()
    }
}
