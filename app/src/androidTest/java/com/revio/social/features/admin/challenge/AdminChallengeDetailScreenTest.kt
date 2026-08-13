package com.revio.social.features.admin.challenge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.data.model.AdminChallenge
import com.revio.social.data.model.ChallengeAdminStatus
import java.time.Instant
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins [AdminChallengeDetailContent]'s read-only rendering: family, required-posts threshold,
 * reward points, the start/end window, timezone, status, and finalizedAt must all be visible for
 * [AdminChallengeDetailUiState.Content] — see the plan's Bloc H3 acceptance criteria. Also covers
 * the Loading/NotFound/Error branches, following the same convention as
 * `AdminChallengesScreenTest`. Exercises the stateless content composable directly — no
 * Hilt/ViewModel needed since the state is passed in.
 */
@RunWith(AndroidJUnit4::class)
class AdminChallengeDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val familyId = UUID.fromString("00000000-0000-0000-0000-0000000000f1")

    private val challenge = AdminChallenge(
        id = UUID.fromString("00000000-0000-0000-0000-00000000000a"),
        title = "Weekend Golf Hunt",
        description = "Find every Golf you can this weekend",
        targetFamilyId = familyId,
        requiredPosts = 5,
        rewardPoints = 300,
        startsAt = Instant.parse("2026-08-07T00:00:00Z"),
        endsAt = Instant.parse("2026-08-09T00:00:00Z"),
        adminTimezone = "Europe/Bucharest",
        status = ChallengeAdminStatus.SCHEDULED,
        createdBy = null,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
        publishedAt = null,
        cancelledAt = null,
        finalizedAt = null,
    )

    @Test
    fun content_afiseaza_familia_pragul_punctele_fereastra_timezone_statusul_si_finalizedAt() {
        composeTestRule.setContent {
            AdminChallengeDetailContent(
                uiState = AdminChallengeDetailUiState.Content(challenge),
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText("Weekend Golf Hunt").assertIsDisplayed()
        composeTestRule.onNodeWithText(familyId.toString()).assertIsDisplayed()
        composeTestRule.onNodeWithText("5").assertIsDisplayed()
        composeTestRule.onNodeWithText("300 pts").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("${challenge.startsAt} → ${challenge.endsAt}")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Europe/Bucharest").assertIsDisplayed()
        composeTestRule.onNodeWithText("SCHEDULED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not finalized").assertIsDisplayed()
    }

    @Test
    fun content_cu_challenge_finalizat_afiseaza_data_finalizedAt() {
        val finalizedAt = Instant.parse("2026-08-09T00:05:00Z")
        composeTestRule.setContent {
            AdminChallengeDetailContent(
                uiState = AdminChallengeDetailUiState.Content(challenge.copy(finalizedAt = finalizedAt)),
                onRetry = {},
            )
        }

        composeTestRule.onNodeWithText(finalizedAt.toString()).assertIsDisplayed()
    }

    @Test
    fun loading_afiseaza_indicatorul_de_progres() {
        composeTestRule.setContent {
            AdminChallengeDetailContent(uiState = AdminChallengeDetailUiState.Loading, onRetry = {})
        }

        composeTestRule.onNodeWithTag("admin_challenge_detail_loading").assertIsDisplayed()
    }

    @Test
    fun notFound_afiseaza_mesajul_Challenge_not_found() {
        composeTestRule.setContent {
            AdminChallengeDetailContent(uiState = AdminChallengeDetailUiState.NotFound, onRetry = {})
        }

        composeTestRule.onNodeWithText("Challenge not found").assertIsDisplayed()
    }

    @Test
    fun eroare_afiseaza_mesajul_si_butonul_Retry_click_declanseaza_onRetry() {
        var retried = false
        composeTestRule.setContent {
            AdminChallengeDetailContent(
                uiState = AdminChallengeDetailUiState.Error("Server error"),
                onRetry = { retried = true },
            )
        }

        composeTestRule.onNodeWithText("Couldn't load challenge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assert(retried)
    }
}
