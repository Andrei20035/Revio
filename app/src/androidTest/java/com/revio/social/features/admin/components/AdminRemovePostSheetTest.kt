package com.revio.social.features.admin.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.data.model.ModerationReason
import com.revio.social.data.model.label
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the plan's own requirement for the reason picker: choosing OTHER with a blank free-text
 * field must leave "Continue" disabled, and only becomes enabled once details are typed. Pure
 * Compose test — AdminRemovePostSheet is stateless w.r.t. the network, so no Hilt/DI is needed.
 */
@RunWith(AndroidJUnit4::class)
class AdminRemovePostSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `no reason selected - Continue is disabled`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun `selecting a non-OTHER reason enables Continue`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.NO_CAR_CONTENT.label).performClick()

        composeTestRule.onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun `selecting OTHER with no details leaves Continue disabled`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performClick()

        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun `selecting OTHER and typing details enables Continue`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performClick()
        composeTestRule.onNodeWithText("Describe the reason").performTextInput("Doesn't fit any category")

        composeTestRule.onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun `blank whitespace-only details for OTHER keeps Continue disabled`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performClick()
        composeTestRule.onNodeWithText("Describe the reason").performTextInput("   ")

        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
    }
}
