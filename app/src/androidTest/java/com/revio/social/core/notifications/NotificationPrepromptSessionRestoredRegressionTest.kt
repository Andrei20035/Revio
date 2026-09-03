package com.revio.social.core.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.data.local.preferences.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Notifications pre-prompt bugfix plan, pas 4 — the exact reported regression, exercised through
 * the real Compose host: [NotificationPrepromptController] is constructed, then
 * [NotificationPrepromptController.onSessionRestored] is called immediately after (no delay of
 * its own in between — mirrors [com.revio.social.core.navigation.StartDestinationViewModel]'s
 * valid-session branch calling it right after the controller is first resolved by Hilt), with
 * [NotificationPrepromptHost] already mounted, in the style of
 * [NotificationPrepromptTourExclusivityTest]. Before the fix, this call was always rejected by
 * `CAMPAIGN_COLD_START_GRACE`/`checkedThisSession`; now the card must actually appear once the
 * grace period elapses, and [NotificationPrepromptController.onShown] must fire exactly once.
 */
@RunWith(AndroidJUnit4::class)
class NotificationPrepromptSessionRestoredRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Mirrors the production `CAMPAIGN_COLD_START_GRACE` in NotificationPrepromptController.kt. */
    private val campaignColdStartGrace: Duration = Duration.ofSeconds(3)

    @Test
    fun onSessionRestored_called_immediately_after_construction_shows_the_card_once_the_grace_elapses() {
        val userId = UUID.randomUUID()
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { this@apply.userId } returns flowOf(userId)
            every { notificationCampaignV1Done(userId) } returns flowOf(false)
            every { onboardingCompleted } returns flowOf(true)
            every { notificationPrepromptShownCount(userId) } returns flowOf(0)
            coEvery { notificationPermissionRequested(userId) } returns false
            coEvery { recordNotificationPrepromptShown(userId) } returns Unit
        }
        val permissionState = mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns false
        }
        // The production constructor (real clock, real Dispatchers.Default) — this exercises the
        // actual on-device timing, not a virtual-time approximation.
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = permissionState,
            clock = Clock.systemUTC().withZone(ZoneOffset.UTC),
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )
        val viewModel = NotificationPrepromptViewModel(controller)

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                NotificationPrepromptHost(viewModel = viewModel)
            }
        }

        // No delay of its own between the controller being constructed above and this call —
        // exactly the real StartDestinationViewModel sequence.
        controller.onSessionRestored()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Someone just liked your spot").assertDoesNotExist()

        Thread.sleep(campaignColdStartGrace.toMillis() + 500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Someone just liked your spot").assertIsDisplayed()
        coVerify(exactly = 1) { prefs.recordNotificationPrepromptShown(userId) }
    }
}
