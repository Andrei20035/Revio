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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins [AdminChallengesContent]'s loading/empty/error/offline branches against the same
 * convention as `AdminReportsScreen.kt:78-119` (isLoading → spinner, isOffline →
 * [com.revio.social.core.ui.components.OfflineStateMessage], errorMessage != null →
 * [com.revio.social.core.ui.components.StateMessage] with a Retry action, isEmpty → StateMessage
 * with no action). Exercises the stateless content composable directly, mirroring
 * `AdminRemovePostSheetTest` — no Hilt/ViewModel needed since the state is passed in.
 */
@RunWith(AndroidJUnit4::class)
class AdminChallengesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val challenge = AdminChallenge(
        id = UUID.fromString("00000000-0000-0000-0000-00000000000a"),
        title = "Weekend Golf Hunt",
        description = null,
        targetFamilyId = UUID.fromString("00000000-0000-0000-0000-0000000000f1"),
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
    fun isLoading_afiseaza_indicatorul_de_progres() {
        composeTestRule.setContent {
            AdminChallengesContent(
                uiState = AdminChallengesUiState(isLoading = true),
                onRetry = {},
                onCreateClick = {},
                onChallengeClick = {},
            )
        }

        composeTestRule.onNodeWithTag("admin_challenges_loading").assertIsDisplayed()
    }

    @Test
    fun stare_goala_afiseaza_mesajul_No_challenges_yet() {
        composeTestRule.setContent {
            AdminChallengesContent(
                uiState = AdminChallengesUiState(challenges = emptyList()),
                onRetry = {},
                onCreateClick = {},
                onChallengeClick = {},
            )
        }

        composeTestRule.onNodeWithText("No challenges yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create the first one to get started.").assertIsDisplayed()
    }

    @Test
    fun eroare_afiseaza_mesajul_si_butonul_Retry_click_declanseaza_onRetry() {
        var retried = false
        composeTestRule.setContent {
            AdminChallengesContent(
                uiState = AdminChallengesUiState(errorMessage = "Server error"),
                onRetry = { retried = true },
                onCreateClick = {},
                onChallengeClick = {},
            )
        }

        composeTestRule.onNodeWithText("Couldn't load challenges").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()

        composeTestRule.onNodeWithText("Retry").performClick()
        assert(retried)
    }

    @Test
    fun offline_afiseaza_OfflineStateMessage_click_pe_Try_again_declanseaza_onRetry() {
        var retried = false
        composeTestRule.setContent {
            AdminChallengesContent(
                uiState = AdminChallengesUiState(isOffline = true, errorMessage = "Network error"),
                onRetry = { retried = true },
                onCreateClick = {},
                onChallengeClick = {},
            )
        }

        composeTestRule.onNodeWithText("You're not connected to the internet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").performClick()
        assert(retried)
    }

    @Test
    fun butonul_Create_este_afisat_indiferent_de_stare() {
        composeTestRule.setContent {
            AdminChallengesContent(
                uiState = AdminChallengesUiState(isLoading = true),
                onRetry = {},
                onCreateClick = {},
                onChallengeClick = {},
            )
        }

        composeTestRule.onNodeWithText("Create").assertIsDisplayed()
    }

    @Test
    fun click_pe_Create_declanseaza_onCreateClick() {
        var createClicked = false
        composeTestRule.setContent {
            AdminChallengesContent(
                uiState = AdminChallengesUiState(challenges = emptyList()),
                onRetry = {},
                onCreateClick = { createClicked = true },
                onChallengeClick = {},
            )
        }

        composeTestRule.onNodeWithText("Create").performClick()
        assert(createClicked)
    }

    @Test
    fun click_pe_un_rand_declanseaza_onChallengeClick_cu_id_ul_challenge_ului() {
        var clickedId: UUID? = null
        composeTestRule.setContent {
            AdminChallengesContent(
                uiState = AdminChallengesUiState(challenges = listOf(challenge)),
                onRetry = {},
                onCreateClick = {},
                onChallengeClick = { clickedId = it },
            )
        }

        assertNull(clickedId)
        composeTestRule.onNodeWithText("Weekend Golf Hunt").performClick()
        assertEquals(challenge.id, clickedId)
    }
}
