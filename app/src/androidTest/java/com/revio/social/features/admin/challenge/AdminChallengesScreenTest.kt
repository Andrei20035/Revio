package com.revio.social.features.admin.challenge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test
    fun isLoading_afiseaza_indicatorul_de_progres() {
        composeTestRule.setContent {
            AdminChallengesContent(
                uiState = AdminChallengesUiState(isLoading = true),
                onRetry = {},
                onCreateClick = {},
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
            )
        }

        composeTestRule.onNodeWithText("Create").performClick()
        assert(createClicked)
    }
}
