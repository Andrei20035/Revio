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
    fun `combined card displays the early spotter number and the bonus points`() {
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
    fun `tapping Got it calls onDismiss`() {
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
    fun `tapping Got it in the host acknowledges via the controller`() {
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
    fun `back press is consumed and does not dismiss or acknowledge the card`() {
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
