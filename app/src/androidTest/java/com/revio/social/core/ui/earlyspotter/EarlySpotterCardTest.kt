package com.revio.social.core.ui.earlyspotter

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.core.earlyspotter.EarlySpotterCardState
import com.revio.social.core.earlyspotter.EarlySpotterController
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.core.tour.TourController
import com.revio.social.core.tour.TourStep
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EarlySpotterCard is pure/presentational (state -> onDismiss), same as TourOverlay —
 * composed directly for the display/dismiss-lambda tests. The ack/back-press behavior lives one
 * level up in EarlySpotterHost, so those tests build a real EarlySpotterHostViewModel around a
 * mocked EarlySpotterController instead of going through Hilt.
 */
@RunWith(AndroidJUnit4::class)
class EarlySpotterCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun hostViewModel(controller: EarlySpotterController) = EarlySpotterHostViewModel(
        earlySpotterController = controller,
        tourController = mockk<TourController>(relaxed = true).apply {
            every { step } returns MutableStateFlow<TourStep?>(null)
        },
        overlayCoordinator = AppOverlayCoordinator(),
    )

    @Test
    fun `combined_card_displays_the_early_spotter_number_and_the_bonus_points`() {
        composeTestRule.setContent {
            EarlySpotterCard(
                state = EarlySpotterCardState.Visible(earlySpotterNumber = 42, bonusPoints = 300),
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("42", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("300", substring = true).assertIsDisplayed()
    }

    @Test
    fun `tapping_Got_it_calls_onDismiss`() {
        var dismissed = false
        composeTestRule.setContent {
            EarlySpotterCard(
                state = EarlySpotterCardState.Visible(earlySpotterNumber = 7, bonusPoints = 300),
                onDismiss = { dismissed = true },
            )
        }

        composeTestRule.onNodeWithText("Got it").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `tapping_Got_it_in_the_host_acknowledges_via_the_controller`() {
        val controller = mockk<EarlySpotterController>(relaxed = true)
        every { controller.state } returns MutableStateFlow(
            EarlySpotterCardState.Visible(earlySpotterNumber = 7, bonusPoints = 300)
        )

        composeTestRule.setContent {
            Box { EarlySpotterHost(viewModel = hostViewModel(controller)) }
        }

        composeTestRule.onNodeWithText("Got it").performClick()

        verify(exactly = 1) { controller.onAcknowledged() }
    }

    @Test
    fun `back_press_is_consumed_and_does_not_dismiss_or_acknowledge_the_card`() {
        val controller = mockk<EarlySpotterController>(relaxed = true)
        every { controller.state } returns MutableStateFlow(
            EarlySpotterCardState.Visible(earlySpotterNumber = 7, bonusPoints = 300)
        )

        composeTestRule.setContent {
            Box { EarlySpotterHost(viewModel = hostViewModel(controller)) }
        }

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        verify(exactly = 0) { controller.onAcknowledged() }
        composeTestRule.onNodeWithText("Got it").assertIsDisplayed()
    }
}
