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
    fun `titlu gol afiseaza Required langa Title`() {
        setContent(CreateChallengeUiState(fieldErrors = mapOf(CreateChallengeField.TITLE to "Required.")))

        composeTestRule.onNodeWithText("Required.").assertIsDisplayed()
    }

    @Test
    fun `titlu peste 150 de caractere afiseaza mesajul de lungime`() {
        setContent(
            CreateChallengeUiState(
                fieldErrors = mapOf(CreateChallengeField.TITLE to "Keep it under 150 characters."),
            ),
        )

        composeTestRule.onNodeWithText("Keep it under 150 characters.").assertIsDisplayed()
    }

    @Test
    fun `requiredPosts 0 afiseaza Must be greater than 0`() {
        setContent(
            CreateChallengeUiState(
                fieldErrors = mapOf(CreateChallengeField.REQUIRED_POSTS to "Must be greater than 0."),
            ),
        )

        composeTestRule.onNodeWithText("Must be greater than 0.").assertIsDisplayed()
    }

    @Test
    fun `requiredPosts abc afiseaza Enter a whole number`() {
        setContent(
            CreateChallengeUiState(
                fieldErrors = mapOf(CreateChallengeField.REQUIRED_POSTS to "Enter a whole number."),
            ),
        )

        composeTestRule.onNodeWithText("Enter a whole number.").assertIsDisplayed()
    }

    @Test
    fun `rewardPoints gol afiseaza Required langa Reward points`() {
        setContent(CreateChallengeUiState(fieldErrors = mapOf(CreateChallengeField.REWARD_POINTS to "Required.")))

        composeTestRule.onNodeWithText("Required.").assertIsDisplayed()
    }

    @Test
    fun `helper text-ul ramane vizibil fara nicio eroare`() {
        setContent(CreateChallengeUiState())

        composeTestRule.onNodeWithText("Qualifying posts each user must publish.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Points granted when they finish.").assertIsDisplayed()
    }

    @Test
    fun `Next ramane activ indiferent de fieldErrors`() {
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
    fun `click pe Next declanseaza NextStep`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("Next").performClick()

        assertEquals(CreateChallengeAction.NextStep, lastAction)
    }

    @Test
    fun `tastare in Title declanseaza UpdateTitle`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("e.g. Weekend Golf Hunt").performTextInput("Weekend Golf Hunt")

        assertEquals(CreateChallengeAction.UpdateTitle("Weekend Golf Hunt"), lastAction)
    }

    @Test
    fun `tastare in Required posts declanseaza UpdateRequiredPosts`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("5").performTextInput("5")

        assertEquals(CreateChallengeAction.UpdateRequiredPosts("5"), lastAction)
    }

    @Test
    fun `Title nu accepta input peste 150 de caractere`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("e.g. Weekend Golf Hunt").performTextInput("A".repeat(160))

        val action = lastAction as? CreateChallengeAction.UpdateTitle
        assertEquals(150, action?.value?.length)
    }

    @Test
    fun `campurile si Next raman vizibile la un FontScale marit`() {
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
