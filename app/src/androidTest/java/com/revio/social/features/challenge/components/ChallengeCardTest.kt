package com.revio.social.features.challenge.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.data.model.ParticipantState
import com.revio.social.data.model.RewardState
import com.revio.social.features.challenge.ChallengeUiState
import com.revio.social.features.challenge.RemainingTime
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the plan's §12 test matrix rows 26–31 for `ChallengeCard`: content rendering, the
 * visual cap at 100% progress, the `GRANTED` treatment, and — the etapa's flagged top risk —
 * that the card body and the nested CTA each trigger only their own callback.
 */
@RunWith(AndroidJUnit4::class)
class ChallengeCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun activeState(
        contributionCount: Int = 3,
        requiredPosts: Int = 5,
        rewardPoints: Int = 300,
        rewardState: RewardState = RewardState.NONE,
        participantState: ParticipantState = ParticipantState.UNKNOWN,
        remaining: RemainingTime = RemainingTime.Days(2),
        titleLine: String = "Spot 5 Volkswagen Golf",
    ) = ChallengeUiState.Active(
        challengeId = UUID.randomUUID(),
        titleLine = titleLine,
        contributionCount = contributionCount,
        requiredPosts = requiredPosts,
        rewardPoints = rewardPoints,
        rewardState = rewardState,
        participantState = participantState,
        endsAt = Instant.parse("2026-08-09T00:00:00Z"),
        remaining = remaining,
    )

    private fun setCardContent(
        state: ChallengeUiState.Active,
        onCardClick: () -> Unit = {},
        onSpotNow: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ChallengeCard(state = state, onCardClick = onCardClick, onSpotNow = onSpotNow)
        }
    }

    @Test
    fun cardul_afiseaza_titlul_progresul_punctele_si_timpul() {
        setCardContent(
            activeState(
                contributionCount = 3,
                requiredPosts = 5,
                rewardPoints = 300,
                remaining = RemainingTime.Days(2),
                titleLine = "Spot 5 Volkswagen Golf",
            ),
        )

        composeTestRule.onNodeWithText("Spot 5 Volkswagen Golf").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 of 5 spotted").assertIsDisplayed()
        composeTestRule.onNodeWithText("300 pts").assertIsDisplayed()
        composeTestRule.onNodeWithText("CHALLENGE · 2 days remaining").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spot now").assertIsDisplayed()
    }

    @Test
    fun contributionCount_peste_requiredPosts_este_plafonat_vizual_la_100_procente() {
        setCardContent(activeState(contributionCount = 7, requiredPosts = 5))

        composeTestRule.onNodeWithText("5 of 5 spotted").assertIsDisplayed()
        composeTestRule.onNodeWithText("7 of 5 spotted").assertDoesNotExist()
    }

    // ---- Copy per stare, tabelul §5.4 al planului ----

    @Test
    fun `stare_-_Activ_incomplet`() {
        setCardContent(
            activeState(
                contributionCount = 3,
                requiredPosts = 5,
                remaining = RemainingTime.Days(2),
            ),
        )

        composeTestRule.onNodeWithText("CHALLENGE · 2 days remaining").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 of 5 spotted").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spot now").assertIsDisplayed()
    }

    @Test
    fun `stare_-_Activ_penultimul_mai_e_un_singur_post`() {
        setCardContent(
            activeState(
                contributionCount = 4,
                requiredPosts = 5,
                remaining = RemainingTime.HoursMinutes(hours = 6, minutes = 12),
            ),
        )

        composeTestRule.onNodeWithText("CHALLENGE · 6h 12m remaining").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 of 5 — one more to go").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spot now").assertIsDisplayed()
    }

    @Test
    fun `stare_-_Activ_prag_atins_recompensa_in_asteptare`() {
        setCardContent(
            activeState(
                contributionCount = 5,
                requiredPosts = 5,
                rewardPoints = 300,
                rewardState = RewardState.NONE,
                participantState = ParticipantState.COMPLETED_PENDING,
            ),
        )

        composeTestRule.onNodeWithText("CHALLENGE · COMPLETE ✓").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 of 5 · 300 pts when it ends").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spot again").assertIsDisplayed()
        // Nu mai arata copy-ul normal de progres o data ce pragul e atins.
        composeTestRule.onNodeWithText("5 of 5 spotted").assertDoesNotExist()
    }

    @Test
    fun `stare_-_Incheiat_prag_atins_acordat_RewardState_GRANTED`() {
        setCardContent(
            activeState(
                contributionCount = 5,
                requiredPosts = 5,
                rewardPoints = 300,
                rewardState = RewardState.GRANTED,
            ),
        )

        composeTestRule.onNodeWithText("CHALLENGE · COMPLETE ✓").assertIsDisplayed()
        composeTestRule.onNodeWithText("+300 pts earned").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spot again").assertIsDisplayed()
        // Nu mai arata copy-ul normal de progres o data ce challenge-ul e complet.
        composeTestRule.onNodeWithText("5 of 5 spotted").assertDoesNotExist()
    }

    @Test
    fun `participantState_UNKNOWN_de_la_un_server_vechi_-_fallback_pe_numarul_de_contributii_pentru_COMPLETED_PENDING`() {
        setCardContent(
            activeState(
                contributionCount = 5,
                requiredPosts = 5,
                rewardPoints = 300,
                rewardState = RewardState.NONE,
                participantState = ParticipantState.UNKNOWN,
            ),
        )

        composeTestRule.onNodeWithText("CHALLENGE · COMPLETE ✓").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 of 5 · 300 pts when it ends").assertIsDisplayed()
    }

    @Test
    fun tap_pe_CTA_declanseaza_doar_onSpotNow() {
        var cardClicks = 0
        var spotNowClicks = 0
        setCardContent(
            activeState(),
            onCardClick = { cardClicks++ },
            onSpotNow = { spotNowClicks++ },
        )

        composeTestRule.onNodeWithText("Spot now").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, cardClicks)
        assertEquals(1, spotNowClicks)
    }

    @Test
    fun tap_pe_card_declanseaza_doar_onCardClick() {
        var cardClicks = 0
        var spotNowClicks = 0
        setCardContent(
            activeState(),
            onCardClick = { cardClicks++ },
            onSpotNow = { spotNowClicks++ },
        )

        // Zona cardului, in afara CTA-ului: titlul.
        composeTestRule.onNodeWithText("Spot 5 Volkswagen Golf").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, cardClicks)
        assertEquals(0, spotNowClicks)
    }

    @Test
    fun contentDescription_agregat_este_prezent_si_distinct_de_CTA() {
        setCardContent(
            activeState(
                contributionCount = 3,
                requiredPosts = 5,
                rewardPoints = 300,
                remaining = RemainingTime.Days(2),
                titleLine = "Spot 5 Volkswagen Golf",
            ),
        )

        composeTestRule
            .onNode(hasContentDescription("Challenge: Spot 5 Volkswagen Golf", substring = true))
            .assertExists()
        composeTestRule
            .onNode(hasContentDescription("3 of 5 spotted", substring = true))
            .assertExists()
        composeTestRule
            .onNode(hasContentDescription("300 points", substring = true))
            .assertExists()
        // Cardul (deschide detaliile) are propria lui descriere, separata de continutul agregat.
        composeTestRule.onNodeWithContentDescription("Open challenge details").assertExists()
    }
}
