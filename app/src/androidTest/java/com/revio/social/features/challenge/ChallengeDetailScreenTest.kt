package com.revio.social.features.challenge

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.ParticipantState
import com.revio.social.data.model.RewardState
import com.revio.social.features.challenge.components.ChallengeCard
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the plan's §6 pas 5 for the Challenge Detail CTA:
 * - the plan's §5.4 "Completed" checkmark badge (pending vs. rewarded, Bloc J4 — unchanged by
 *   this pas);
 * - the CTA itself now always renders (no more gate on `effectiveStatus == ACTIVE`), driven by
 *   the same [challengeCta] the Feed card uses, across ACTIVE-incomplete / goal-reached /
 *   granted / ended / cancelled / scheduled, including the `Completed` priority rule over a
 *   `Challenge ended` challenge whose reward was already granted;
 * - that [ChallengeCard] and [ChallengeDetailContent] render an *identical* label + enabled
 *   state for the same input data — the plan's own acceptance criterion for this pas.
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
        participantState: ParticipantState = ParticipantState.UNKNOWN,
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
        participantState = participantState,
        remaining = RemainingTime.Days(1),
        contributions = emptyList(),
    )

    // ---------- Bloc J4: "Completed" checkmark badge (unchanged by this pas) ----------

    @Test
    fun `prag_atins_recompensa_neacordata_-_Completed_cu_linia_de_asteptare`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 5,
                    requiredPosts = 5,
                    rewardState = RewardState.NONE,
                    participantState = ParticipantState.COMPLETED_PENDING,
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
                    participantState = ParticipantState.REWARDED,
                    effectiveStatus = EffectiveChallengeStatus.ENDED,
                ),
                onSpotNow = {},
            )
        }

        composeTestRule.onNodeWithText("✓").assertIsDisplayed()
        // "+300 pts earned" apare de doua ori: bifa noua si cardul REWARD existent, ambele corecte.
        composeTestRule.onAllNodesWithText("+300 pts earned").assertCountEquals(2)
        composeTestRule.onNodeWithText("Reward pending until challenge ends").assertDoesNotExist()
        // "Completed" apare de doua ori: bifa checkmark si CTA-ul dezactivat (eyebrow-ul de sus
        // e "COMPLETED", cu majuscule — un text diferit, case-sensitive). Regula de prioritate
        // (§3) face ca un challenge ENDED cu recompensa acordata sa arate tot "Completed", nu
        // "Challenge ended".
        composeTestRule.onAllNodesWithText("Completed").assertCountEquals(2)
        composeTestRule.onNodeWithText("Challenge ended").assertDoesNotExist()
    }

    @Test
    fun `prag_neatins_-_fara_bifa_Completed`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 3,
                    requiredPosts = 5,
                    rewardState = RewardState.NONE,
                    participantState = ParticipantState.IN_PROGRESS,
                    effectiveStatus = EffectiveChallengeStatus.ACTIVE,
                ),
                onSpotNow = {},
            )
        }

        composeTestRule.onNodeWithText("Reward pending until challenge ends").assertDoesNotExist()
        composeTestRule.onNodeWithText("✓").assertDoesNotExist()
    }

    // ---------- CTA: always visible, label/enabled driven by challengeCta (pas 5) ----------

    @Test
    fun `CTA activ incomplet fara postari inca - Spot now activ`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 0,
                    requiredPosts = 5,
                    participantState = ParticipantState.IN_PROGRESS,
                    effectiveStatus = EffectiveChallengeStatus.ACTIVE,
                ),
                onSpotNow = {},
            )
        }

        composeTestRule.onNodeWithText("Spot now").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spot now").assertIsEnabled()
    }

    @Test
    fun `CTA activ incomplet cu postari - Spot again activ`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 3,
                    requiredPosts = 5,
                    participantState = ParticipantState.IN_PROGRESS,
                    effectiveStatus = EffectiveChallengeStatus.ACTIVE,
                ),
                onSpotNow = {},
            )
        }

        composeTestRule.onNodeWithText("Spot again").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spot again").assertIsEnabled()
    }

    @Test
    fun `CTA prag atins recompensa in asteptare - Goal reached dezactivat`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 5,
                    requiredPosts = 5,
                    participantState = ParticipantState.COMPLETED_PENDING,
                    effectiveStatus = EffectiveChallengeStatus.ACTIVE,
                ),
                onSpotNow = {},
            )
        }

        composeTestRule.onNodeWithText("CHALLENGE · GOAL REACHED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Goal reached").assertIsDisplayed()
        composeTestRule.onNodeWithText("Goal reached").assertIsNotEnabled()
    }

    @Test
    fun `CTA recompensa acordata - Completed dezactivat`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 5,
                    requiredPosts = 5,
                    rewardState = RewardState.GRANTED,
                    participantState = ParticipantState.REWARDED,
                    effectiveStatus = EffectiveChallengeStatus.ENDED,
                ),
                onSpotNow = {},
            )
        }

        // Badge checkmark's "Completed" (index 0, not interactive) + the CTA's (index 1) — the
        // eyebrow's "COMPLETED" (uppercase) is a separate, case-sensitive match.
        val completedNodes = composeTestRule.onAllNodesWithText("Completed")
        completedNodes.assertCountEquals(2)
        completedNodes[1].assertIsNotEnabled()
    }

    @Test
    fun `CTA challenge incheiat fara prag atins - Challenge ended dezactivat, nu ascuns`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 2,
                    requiredPosts = 5,
                    participantState = ParticipantState.NOT_COMPLETED,
                    effectiveStatus = EffectiveChallengeStatus.ENDED,
                ),
                onSpotNow = {},
            )
        }

        composeTestRule.onNodeWithText("Challenge ended").assertIsDisplayed()
        composeTestRule.onNodeWithText("Challenge ended").assertIsNotEnabled()
    }

    @Test
    fun `CTA challenge anulat - Challenge cancelled dezactivat`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 0,
                    requiredPosts = 5,
                    participantState = ParticipantState.CANCELLED,
                    effectiveStatus = EffectiveChallengeStatus.CANCELLED,
                ),
                onSpotNow = {},
            )
        }

        composeTestRule.onNodeWithText("Challenge cancelled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Challenge cancelled").assertIsNotEnabled()
    }

    @Test
    fun `CTA challenge programat - Not started yet dezactivat`() {
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 0,
                    requiredPosts = 5,
                    participantState = ParticipantState.NOT_STARTED,
                    effectiveStatus = EffectiveChallengeStatus.SCHEDULED,
                ),
                onSpotNow = {},
            )
        }

        composeTestRule.onNodeWithText("Not started yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not started yet").assertIsNotEnabled()
    }

    @Test
    fun `tap pe CTA dezactivat de pe Detail nu declanseaza onSpotNow`() {
        var spotNowClicks = 0
        composeTestRule.setContent {
            ChallengeDetailContent(
                state = contentState(
                    contributionCount = 5,
                    requiredPosts = 5,
                    participantState = ParticipantState.COMPLETED_PENDING,
                    effectiveStatus = EffectiveChallengeStatus.ACTIVE,
                ),
                onSpotNow = { spotNowClicks++ },
            )
        }

        composeTestRule.onNodeWithText("Goal reached").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, spotNowClicks)
    }

    // ---------- Card vs. Detail: identical label + enabled for the same input (acceptance criterion) ----------

    private data class CtaCase(
        val name: String,
        val effectiveStatus: EffectiveChallengeStatus,
        val participantState: ParticipantState,
        val rewardState: RewardState,
        val contributionCount: Int,
        val requiredPosts: Int,
        val expectedLabel: String,
        val expectedEnabled: Boolean,
    )

    // Every case below has a label distinct from the others, so onAllNodesWithText(expectedLabel)
    // unambiguously picks up exactly the Card + Detail pair rendered for that one case.
    // "Completed" (the granted case) is deliberately excluded here — Detail's own checkmark
    // badge also prints the literal text "Completed" whenever the threshold is reached, which
    // would pollute the count; it has its own dedicated test above instead.
    private val ctaCases = listOf(
        CtaCase(
            name = "activ, zero postari",
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.IN_PROGRESS,
            rewardState = RewardState.NONE,
            contributionCount = 0,
            requiredPosts = 5,
            expectedLabel = "Spot now",
            expectedEnabled = true,
        ),
        CtaCase(
            name = "activ, cu postari",
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.IN_PROGRESS,
            rewardState = RewardState.NONE,
            contributionCount = 3,
            requiredPosts = 5,
            expectedLabel = "Spot again",
            expectedEnabled = true,
        ),
        CtaCase(
            name = "prag atins, recompensa in asteptare",
            effectiveStatus = EffectiveChallengeStatus.ACTIVE,
            participantState = ParticipantState.COMPLETED_PENDING,
            rewardState = RewardState.NONE,
            contributionCount = 5,
            requiredPosts = 5,
            expectedLabel = "Goal reached",
            expectedEnabled = false,
        ),
        CtaCase(
            name = "incheiat, prag neatins",
            effectiveStatus = EffectiveChallengeStatus.ENDED,
            participantState = ParticipantState.NOT_COMPLETED,
            rewardState = RewardState.NONE,
            contributionCount = 2,
            requiredPosts = 5,
            expectedLabel = "Challenge ended",
            expectedEnabled = false,
        ),
        CtaCase(
            name = "anulat",
            effectiveStatus = EffectiveChallengeStatus.CANCELLED,
            participantState = ParticipantState.CANCELLED,
            rewardState = RewardState.NONE,
            contributionCount = 0,
            requiredPosts = 5,
            expectedLabel = "Challenge cancelled",
            expectedEnabled = false,
        ),
        CtaCase(
            name = "programat",
            effectiveStatus = EffectiveChallengeStatus.SCHEDULED,
            participantState = ParticipantState.NOT_STARTED,
            rewardState = RewardState.NONE,
            contributionCount = 0,
            requiredPosts = 5,
            expectedLabel = "Not started yet",
            expectedEnabled = false,
        ),
    )

    @Test
    fun `ChallengeCard si ChallengeDetailContent randeaza acelasi label si enabled pentru aceleasi date`() {
        composeTestRule.setContent {
            Column {
                ctaCases.forEach { case ->
                    ChallengeCard(
                        state = ChallengeUiState.Active(
                            challengeId = UUID.randomUUID(),
                            titleLine = "Spot 5 Volkswagen Golf",
                            contributionCount = case.contributionCount,
                            requiredPosts = case.requiredPosts,
                            rewardPoints = 300,
                            rewardState = case.rewardState,
                            participantState = case.participantState,
                            effectiveStatus = case.effectiveStatus,
                            endsAt = Instant.parse("2026-08-09T00:00:00Z"),
                            remaining = RemainingTime.Days(1),
                        ),
                        onCardClick = {},
                        onSpotNow = {},
                    )
                    ChallengeDetailContent(
                        state = contentState(
                            contributionCount = case.contributionCount,
                            requiredPosts = case.requiredPosts,
                            rewardState = case.rewardState,
                            participantState = case.participantState,
                            effectiveStatus = case.effectiveStatus,
                        ),
                        onSpotNow = {},
                    )
                }
            }
        }

        ctaCases.forEach { case ->
            val nodes = composeTestRule.onAllNodesWithText(case.expectedLabel)
            nodes.assertCountEquals(2)
            if (case.expectedEnabled) {
                nodes[0].assertIsEnabled()
                nodes[1].assertIsEnabled()
            } else {
                nodes[0].assertIsNotEnabled()
                nodes[1].assertIsNotEnabled()
            }
        }
    }
}
