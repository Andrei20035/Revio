package com.revio.social.features.upload

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.core.ui.overlay.InfoOverlay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the vehicle-locked field wiring from ImageUploadScreen (plan §9-Pas11): tapping the
 * locked brand/model field opens [InfoOverlay] instead of the dropdown, the overlay dismisses
 * via its standard tap-outside behavior, and the description field stays editable throughout —
 * mirrors the small-composable style of LocationStatusChipTest rather than driving the full
 * Hilt-backed screen.
 */
@RunWith(AndroidJUnit4::class)
class VehicleLockedFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val overlayTitle = "Vehicle locked"
    private val overlayMessage = "The brand and model can't be changed because this post has " +
        "contributed to a challenge. You can still edit the description."

    /** Same wiring as ImageUploadScreen: a locked field opens InfoOverlay on click. */
    @Composable
    private fun Harness(locked: Boolean) {
        var showOverlay by remember { mutableStateOf(false) }
        var description by remember { mutableStateOf("") }

        Column {
            UploadDropdownField(
                placeholder = "Brand",
                value = "Lamborghini",
                enabled = true,
                locked = locked,
                loading = false,
                onClick = { if (locked) showOverlay = true },
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.testTag("description_field"),
            )
        }

        if (showOverlay) {
            InfoOverlay(title = overlayTitle, message = overlayMessage, onDismiss = { showOverlay = false })
        }
    }

    @Test
    fun tappingLockedField_showsTheVehicleLockedOverlay() {
        composeTestRule.setContent { Harness(locked = true) }

        composeTestRule.onNodeWithText("Lamborghini").performClick()

        composeTestRule.onNodeWithText(overlayTitle).assertIsDisplayed()
    }

    @Test
    fun tappingOutsideTheOverlay_dismissesIt() {
        composeTestRule.setContent { Harness(locked = true) }
        composeTestRule.onNodeWithText("Lamborghini").performClick()
        composeTestRule.onNodeWithText(overlayTitle).assertIsDisplayed()

        composeTestRule.onRoot().performTouchInput { click(Offset(1f, 1f)) }

        composeTestRule.onNodeWithText(overlayTitle).assertDoesNotExist()
    }

    @Test
    fun descriptionField_staysEditable_whileTheVehicleIsLocked() {
        composeTestRule.setContent { Harness(locked = true) }

        composeTestRule.onNodeWithText("Lamborghini").performClick()
        composeTestRule.onRoot().performTouchInput { click(Offset(1f, 1f)) }

        composeTestRule.onNodeWithTag("description_field").performTextInput("still a Huracan")

        composeTestRule.onNodeWithText("still a Huracan").assertIsDisplayed()
    }
}
