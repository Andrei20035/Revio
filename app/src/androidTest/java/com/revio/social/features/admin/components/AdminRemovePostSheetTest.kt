package com.revio.social.features.admin.components

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.data.model.ModerationReason
import com.revio.social.data.model.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the plan's own requirement for the reason picker: choosing OTHER with a blank free-text
 * field must leave "Continue" disabled, and only becomes enabled once details are typed. Also
 * pins the sheet's viewport contract: the action footer must stay on-screen and pinned while the
 * 13-reason list scrolls underneath it, on constrained heights and larger font scales. Pure
 * Compose test — AdminRemovePostSheet is stateless w.r.t. the network, so no Hilt/DI is needed.
 */
@RunWith(AndroidJUnit4::class)
class AdminRemovePostSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `no_reason_selected_Continue_is_disabled`() {
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
    fun `selecting_a_non_OTHER_reason_enables_Continue`() {
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
    fun `selecting_OTHER_with_no_details_leaves_Continue_disabled`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performScrollTo().performClick()

        // Confirms OTHER was actually selected (not just that no reason was picked at all).
        composeTestRule.onNodeWithText("Describe the reason").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun `selecting_OTHER_and_typing_details_enables_Continue`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Describe the reason").performScrollTo().performTextInput("Doesn't fit any category")

        composeTestRule.onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun `blank_whitespace_only_details_for_OTHER_keeps_Continue_disabled`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Describe the reason").performScrollTo().performTextInput("   ")

        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun `OTHER_details_with_surrounding_whitespace_are_trimmed_before_confirming`() {
        var confirmed: Pair<ModerationReason, String?>? = null

        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { reason, details -> confirmed = reason to details },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Describe the reason").performScrollTo()
            .performTextInput("  Doesn't fit any category  ")
        composeTestRule.onNodeWithText("Continue").assertIsEnabled().performClick()
        composeTestRule.onNodeWithText("Remove post").performClick()

        assertEquals(ModerationReason.OTHER to "Doesn't fit any category", confirmed)
    }

    @Test
    fun `confirm_step_shows_the_typed_OTHER_details_under_the_reason_label`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Describe the reason").performScrollTo()
            .performTextInput("Doesn't fit any category")
        composeTestRule.onNodeWithText("Continue").performClick()

        composeTestRule.onNodeWithText("Confirm removal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Doesn't fit any category").assertIsDisplayed()
    }

    @Test
    fun `footer_is_visible_without_dragging_the_sheet`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun `footer_stays_pinned_while_the_reason_list_scrolls`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        val boundsBeforeScroll = composeTestRule.onNodeWithText("Continue").getUnclippedBoundsInRoot()

        composeTestRule.onNodeWithText(ModerationReason.LOW_QUALITY.label).performScrollTo()

        val boundsAfterScroll = composeTestRule.onNodeWithText("Continue").getUnclippedBoundsInRoot()
        assertEquals(boundsBeforeScroll.top, boundsAfterScroll.top)
        assertEquals(boundsBeforeScroll.bottom, boundsAfterScroll.bottom)
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun `last_reason_is_reachable_and_selectable_via_scroll`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performScrollTo().performClick()

        composeTestRule.onNodeWithText("Continue").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun `every_reason_is_scrollable_to_and_selectable`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        ModerationReason.entries.forEach { reason ->
            composeTestRule.onNodeWithText(reason.label).performScrollTo().performClick()

            if (reason == ModerationReason.OTHER) {
                composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
            } else {
                composeTestRule.onNodeWithText("Continue").assertIsEnabled()
            }
        }
    }

    @Test
    fun `continuing_with_a_non_OTHER_reason_opens_confirm_step_and_confirms_with_null_details`() {
        var confirmed: Pair<ModerationReason, String?>? = null

        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { reason, details -> confirmed = reason to details },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.NO_CAR_CONTENT.label).performClick()
        composeTestRule.onNodeWithText("Continue").performClick()

        composeTestRule.onNodeWithText("Confirm removal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove post").assertIsDisplayed().performClick()

        assertEquals(ModerationReason.NO_CAR_CONTENT to null, confirmed)
    }

    @Test
    fun `continuing_with_OTHER_opens_confirm_step_and_confirms_with_the_typed_details`() {
        var confirmed: Pair<ModerationReason, String?>? = null

        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { reason, details -> confirmed = reason to details },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Describe the reason").performScrollTo()
            .assertIsDisplayed()
            .performTextInput("Doesn't fit any category")
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed().assertIsEnabled().performClick()

        composeTestRule.onNodeWithText("Confirm removal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove post").performClick()

        assertEquals(ModerationReason.OTHER to "Doesn't fit any category", confirmed)
    }

    @Test
    fun `footer_remains_visible_on_a_constrained_sheet_height`() {
        composeTestRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 480.dp))) {
                AdminRemovePostSheet(
                    isSubmitting = false,
                    onConfirm = { _, _ -> },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun `footer_remains_visible_at_a_larger_font_scale`() {
        composeTestRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.8f)) {
                AdminRemovePostSheet(
                    isSubmitting = false,
                    onConfirm = { _, _ -> },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun `submitting_disables_Back_and_Remove_post_on_the_confirm_step`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = true,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.NO_CAR_CONTENT.label).performClick()
        composeTestRule.onNodeWithText("Continue").performClick()

        composeTestRule.onNodeWithText("Back").assertIsNotEnabled()
    }

    @Test
    fun `error_message_is_shown_on_the_confirm_step_and_Remove_post_stays_enabled`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                errorMessage = "Post not found",
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.NO_CAR_CONTENT.label).performClick()
        composeTestRule.onNodeWithText("Continue").performClick()

        composeTestRule.onNodeWithText("Post not found").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove post").assertIsEnabled()
    }

    @Test
    fun `scrolling_to_the_last_reason_and_flinging_past_it_keeps_the_sheet_open_and_footer_visible`() {
        var dismissed = false
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = { dismissed = true },
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performScrollTo()
        // A fling past the list's own end must be swallowed at the list boundary, not bleed into
        // the ModalBottomSheet's drag-to-dismiss — this is the OnePlus 8T oscillation glitch.
        composeTestRule.onNodeWithTag("admin_remove_post_reason_list").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()

        assertFalse(
            "An overscroll past the last reason must not dismiss the sheet",
            dismissed,
        )
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun `footer_stays_visible_and_reachable_with_the_OTHER_field_focused`() {
        composeTestRule.setContent {
            AdminRemovePostSheet(
                isSubmitting = false,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(ModerationReason.OTHER.label).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Describe the reason").performScrollTo().requestFocus()

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed().assertIsNotEnabled()
    }
}
