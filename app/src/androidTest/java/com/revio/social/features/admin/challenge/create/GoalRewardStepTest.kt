package com.revio.social.features.admin.challenge.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins [GoalRewardStep]'s per-field validation messages (see [validateGoalStep]), the fact that
 * `Next` stays enabled here (unlike Vehicle's — clicking it is what surfaces the errors), and
 * that the always-visible hint text survives a larger font scale (the `AdminRemovePostSheetTest`
 * pattern). Exercises the stateless composable directly, no Hilt.
 */
@RunWith(AndroidJUnit4::class)
class GoalRewardStepTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(uiState: CreateChallengeUiState, onAction: (CreateChallengeAction) -> Unit = {}) {
        composeTestRule.setContent { ScrollableGoalRewardStep(uiState = uiState, onAction = onAction) }
    }

    @Test
    fun `titlu_gol_afiseaza_Required_langa_Title`() {
        setContent(CreateChallengeUiState(fieldErrors = mapOf(CreateChallengeField.TITLE to "Required.")))

        composeTestRule.onNodeWithText("Required.").assertIsDisplayed()
    }

    @Test
    fun `titlu_peste_150_de_caractere_afiseaza_mesajul_de_lungime`() {
        setContent(
            CreateChallengeUiState(
                fieldErrors = mapOf(CreateChallengeField.TITLE to "Keep it under 150 characters."),
            ),
        )

        composeTestRule.onNodeWithText("Keep it under 150 characters.").assertIsDisplayed()
    }

    @Test
    fun `requiredPosts_0_afiseaza_Must_be_greater_than_0`() {
        setContent(
            CreateChallengeUiState(
                fieldErrors = mapOf(CreateChallengeField.REQUIRED_POSTS to "Must be greater than 0."),
            ),
        )

        composeTestRule.onNodeWithText("Must be greater than 0.").assertIsDisplayed()
    }

    @Test
    fun `requiredPosts_abc_afiseaza_Enter_a_whole_number`() {
        setContent(
            CreateChallengeUiState(
                fieldErrors = mapOf(CreateChallengeField.REQUIRED_POSTS to "Enter a whole number."),
            ),
        )

        composeTestRule.onNodeWithText("Enter a whole number.").assertIsDisplayed()
    }

    @Test
    fun `rewardPoints_gol_afiseaza_Required_langa_Reward_points`() {
        setContent(CreateChallengeUiState(fieldErrors = mapOf(CreateChallengeField.REWARD_POINTS to "Required.")))

        composeTestRule.onNodeWithText("Required.").assertIsDisplayed()
    }

    @Test
    fun `helper_text-ul_ramane_vizibil_fara_nicio_eroare`() {
        setContent(CreateChallengeUiState())

        composeTestRule.onNodeWithText("Qualifying posts each user must publish.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Points granted when they finish.").assertIsDisplayed()
    }

    @Test
    fun `Next_ramane_activ_indiferent_de_fieldErrors`() {
        setContent(
            CreateChallengeUiState(
                fieldErrors = mapOf(
                    CreateChallengeField.TITLE to "Required.",
                    CreateChallengeField.REQUIRED_POSTS to "Required.",
                    CreateChallengeField.REWARD_POINTS to "Required.",
                ),
            ),
        )

        composeTestRule.onNodeWithText("Next").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun `click_pe_Next_declanseaza_NextStep`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("Next").performClick()

        assertEquals(CreateChallengeAction.NextStep, lastAction)
    }

    @Test
    fun `tastare_in_Title_declanseaza_UpdateTitle`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("e.g. Weekend Golf Hunt").performTextInput("Weekend Golf Hunt")

        assertEquals(CreateChallengeAction.UpdateTitle("Weekend Golf Hunt"), lastAction)
    }

    @Test
    fun `tastare_in_Required_posts_declanseaza_UpdateRequiredPosts`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("5").performTextInput("5")

        assertEquals(CreateChallengeAction.UpdateRequiredPosts("5"), lastAction)
    }

    @Test
    fun `Title_nu_accepta_input_peste_150_de_caractere`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("e.g. Weekend Golf Hunt").performTextInput("A".repeat(160))

        val action = lastAction as? CreateChallengeAction.UpdateTitle
        assertEquals(150, action?.value?.length)
    }

    @Test
    fun `campurile_si_Next_raman_vizibile_la_un_FontScale_marit`() {
        composeTestRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.8f)) {
                ScrollableGoalRewardStep(uiState = CreateChallengeUiState(), onAction = {})
            }
        }

        composeTestRule.onNodeWithText("What does it take?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Qualifying posts each user must publish.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Points granted when they finish.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
    }
}

/** [GoalRewardStep] relies on its host screen for scrolling (see `CreateChallengeScreen.kt`) —
 * wrapped here so the larger-font-scale test can actually reach the bottom of the content. */
@Composable
private fun ScrollableGoalRewardStep(uiState: CreateChallengeUiState, onAction: (CreateChallengeAction) -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        GoalRewardStep(uiState = uiState, onAction = onAction)
    }
}
