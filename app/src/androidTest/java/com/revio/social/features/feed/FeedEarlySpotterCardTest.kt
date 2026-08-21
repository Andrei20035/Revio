package com.revio.social.features.feed

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.core.earlyspotter.EarlySpotterCardState
import com.revio.social.core.earlyspotter.EarlySpotterController
import com.revio.social.core.overlay.ActiveOverlay
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.core.tour.TourController
import com.revio.social.core.tour.TourStep
import com.revio.social.core.ui.earlyspotter.EarlySpotterHost
import com.revio.social.core.ui.earlyspotter.EarlySpotterHostViewModel
import com.revio.social.core.ui.feedback.FirstPostFeedbackCardCoordinator
import com.revio.social.core.ui.feedback.FirstPostFeedbackCardState
import com.revio.social.core.ui.feedback.FirstPostFeedbackHost
import com.revio.social.core.ui.feedback.FirstPostFeedbackStep
import com.revio.social.core.ui.feedback.FirstPostFeedbackViewModel
import com.revio.social.data.model.FeedbackSurface
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises EarlySpotterHost the way it's actually composed in FeedScreen: inside a foreground
 * Box slot alongside other content. Uses a manually-built EarlySpotterHostViewModel (mocked
 * EarlySpotterController) instead of pulling in Hilt/NavController, same rationale as
 * EarlySpotterCardTest.
 */
@RunWith(AndroidJUnit4::class)
class FeedEarlySpotterCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun earlySpotterHostViewModel(
        state: EarlySpotterCardState,
        overlayCoordinator: AppOverlayCoordinator,
    ) = EarlySpotterHostViewModel(
        earlySpotterController = mockk<EarlySpotterController>(relaxed = true).apply {
            every { this@apply.state } returns MutableStateFlow(state)
        },
        tourController = mockk<TourController>(relaxed = true).apply {
            every { step } returns MutableStateFlow<TourStep?>(null)
        },
        overlayCoordinator = overlayCoordinator,
    )

    @Test
    fun `visible_state_shows_the_card_and_its_scrim_blocks_touches_underneath`() {
        var underlyingTapped = false
        val hostViewModel = earlySpotterHostViewModel(
            state = EarlySpotterCardState.Visible(earlySpotterNumber = 7, bonusPoints = 300),
            overlayCoordinator = AppOverlayCoordinator(),
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { underlyingTapped = true } },
                )
                EarlySpotterHost(viewModel = hostViewModel)
            }
        }

        composeTestRule.onNodeWithText("You are an Early Spotter!").assertIsDisplayed()

        composeTestRule.onRoot().performTouchInput { click(Offset(10f, 10f)) }

        assertFalse("scrim must consume the touch before it reaches the content underneath", underlyingTapped)
    }

    /**
     * Regression: the combined Early Spotter card (highest-priority overlay, see
     * AppOverlayCoordinator) and FirstPostFeedbackHost (FeedScreen.kt:390) must never both be
     * visible at once — FirstPostFeedbackHost's own isTourActive gate (backed by the same shared
     * coordinator) is what's supposed to keep it hidden while Early Spotter is active.
     */
    @Test
    fun `Early_Spotter_card_and_FirstPostFeedbackHost_never_overlap`() {
        val sharedCoordinator = AppOverlayCoordinator()
        val earlySpotterHostViewModel = earlySpotterHostViewModel(
            state = EarlySpotterCardState.Visible(earlySpotterNumber = 7, bonusPoints = 300),
            overlayCoordinator = sharedCoordinator,
        )

        // Same construction FirstPostFeedbackController.kt uses for its own isTourActive, applied
        // manually here instead of pulling in that class's full dependency chain.
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val isTourActive = sharedCoordinator.isBlockedByFlow(ActiveOverlay.FirstPostFeedback)
            .stateIn(testScope, SharingStarted.Eagerly, sharedCoordinator.isBlockedBy(ActiveOverlay.FirstPostFeedback))

        val firstPostFeedbackCardCoordinator = mockk<FirstPostFeedbackCardCoordinator>(relaxed = true).apply {
            every { cardState } returns MutableStateFlow(FirstPostFeedbackCardState(step = FirstPostFeedbackStep.Rating))
            every { confirmationMessage } returns MutableStateFlow(null)
            every { this@apply.isTourActive } returns isTourActive
        }
        val firstPostFeedbackViewModel = FirstPostFeedbackViewModel(firstPostFeedbackCardCoordinator)

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                EarlySpotterHost(viewModel = earlySpotterHostViewModel)
                FirstPostFeedbackHost(
                    surface = FeedbackSurface.FEED,
                    isBlocked = { false },
                    viewModel = firstPostFeedbackViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("You are an Early Spotter!").assertIsDisplayed()
        composeTestRule.onNodeWithText("What made posting difficult?").assertDoesNotExist()
    }
}
