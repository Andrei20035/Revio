package com.revio.social.core.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.core.overlay.ActiveOverlay
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.core.tour.TourStep
import com.revio.social.core.ui.tour.TourOverlay
import com.revio.social.data.local.preferences.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 2.1 regression, in the style of `FeedEarlySpotterCardTest
 * .Early_Spotter_card_and_FirstPostFeedbackHost_never_overlap`: the guided tour (highest-priority
 * overlay) and [NotificationPrepromptHost] (lowest, [ActiveOverlay.NotificationPreprompt]) must
 * never both be visible at once. Unlike [com.revio.social.core.ui.feedback.FirstPostFeedbackHost]
 * (which self-gates reactively on `isTourActive`), [NotificationPrepromptController] only checks
 * [AppOverlayCoordinator.isBlockedBy] once, right before flipping to visible (step 2.1) — so this
 * exercises that one-time gate via a real [NotificationPrepromptController].
 */
@RunWith(AndroidJUnit4::class)
class NotificationPrepromptTourExclusivityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildController(overlayCoordinator: AppOverlayCoordinator): NotificationPrepromptController {
        val userId = UUID.randomUUID()
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { this@apply.userId } returns flowOf(userId)
            every { notificationPrepromptShownCount(userId) } returns flowOf(0)
            every { notificationPrepromptLastShownAt(userId) } returns flowOf(null)
            coEvery { notificationPermissionRequested(userId) } returns false
            coEvery { recordNotificationPrepromptShown(userId) } returns Unit
        }
        val permissionState = mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns false
        }
        return NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = permissionState,
            clock = Clock.systemUTC().withZone(ZoneOffset.UTC),
            appOverlayCoordinator = overlayCoordinator,
            analyticsClient = null,
        )
    }

    @Test
    fun tour_and_notification_preprompt_never_overlap() {
        val sharedCoordinator = AppOverlayCoordinator()
        sharedCoordinator.setActive(ActiveOverlay.Tour, true)
        val controller = buildController(sharedCoordinator)
        val viewModel = NotificationPrepromptViewModel(controller)

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                TourOverlay(
                    step = TourStep.Feed,
                    spotlight = null,
                    onAdvance = {},
                    onPostCta = {},
                )
                NotificationPrepromptHost(viewModel = viewModel)
            }
        }

        // The trigger fires while the tour is active — isBlockedBy(NotificationPreprompt) must
        // keep the card from ever flipping to visible. The controller launches its own check on
        // Dispatchers.Default (fire-and-forget by design), so a short real wait is needed before
        // waitForIdle() re-syncs Compose to the (unchanged) resulting state.
        controller.onLoginObserved()
        Thread.sleep(200)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Every scroll could hide a gem.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Someone just liked your spot").assertDoesNotExist()
    }
}
