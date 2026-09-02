package com.revio.social.core.notifications

import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.overlay.ActiveOverlay
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.data.local.preferences.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class NotificationPrepromptControllerTest {

    private val testUserId: UUID = UUID.randomUUID()
    private val fixedNow: Instant = Instant.parse("2026-01-15T12:00:00Z")

    private fun buildController(
        notificationsAlreadyEnabled: Boolean = false,
        shownCount: Int = 0,
        lastShownAt: Instant? = null,
        appOverlayCoordinator: AppOverlayCoordinator = AppOverlayCoordinator(),
        likesChannelBlocked: Boolean = false,
    ): NotificationPrepromptController {
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { userId } returns flowOf(testUserId)
            every { notificationPrepromptShownCount(testUserId) } returns flowOf(shownCount)
            every { notificationPrepromptLastShownAt(testUserId) } returns flowOf(lastShownAt)
            coEvery { notificationPermissionRequested(testUserId) } returns false
            coEvery { recordNotificationPrepromptShown(testUserId) } returns Unit
        }
        val permissionState = mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns notificationsAlreadyEnabled
            every { isChannelBlocked("likes") } returns likesChannelBlocked
        }
        val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
        return NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = permissionState,
            clock = clock,
            appOverlayCoordinator = appOverlayCoordinator,
            analyticsClient = null,
        )
    }

    @Test
    fun `onLoginObserved shows the card when eligible`() = runTest {
        val controller = buildController()

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertTrue(controller.uiState.value.visible)
    }

    @Test
    fun `onLoginObserved does not record a show until onShown is called`() = runTest {
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { userId } returns flowOf(testUserId)
            every { notificationPrepromptShownCount(testUserId) } returns flowOf(0)
            every { notificationPrepromptLastShownAt(testUserId) } returns flowOf(null)
            coEvery { notificationPermissionRequested(testUserId) } returns false
            coEvery { recordNotificationPrepromptShown(testUserId) } returns Unit
        }
        val permissionState = mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns false
        }
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = permissionState,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertTrue(controller.uiState.value.visible)
        coVerify(exactly = 0) { prefs.recordNotificationPrepromptShown(testUserId) }

        controller.onShown()
        advanceUntilStateSettles()

        coVerify(exactly = 1) { prefs.recordNotificationPrepromptShown(testUserId) }
    }

    @Test
    fun `onShown only records the show once per pending show`() = runTest {
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { userId } returns flowOf(testUserId)
            every { notificationPrepromptShownCount(testUserId) } returns flowOf(0)
            every { notificationPrepromptLastShownAt(testUserId) } returns flowOf(null)
            coEvery { notificationPermissionRequested(testUserId) } returns false
            coEvery { recordNotificationPrepromptShown(testUserId) } returns Unit
        }
        val permissionState = mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns false
        }
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = permissionState,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )

        controller.onLoginObserved()
        advanceUntilStateSettles()
        controller.onShown()
        controller.onShown()
        advanceUntilStateSettles()

        coVerify(exactly = 1) { prefs.recordNotificationPrepromptShown(testUserId) }
    }

    @Test
    fun `dismissing before onShown un-claims the per-process check`() = runTest {
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { userId } returns flowOf(testUserId)
            every { notificationPrepromptShownCount(testUserId) } returns flowOf(0)
            every { notificationPrepromptLastShownAt(testUserId) } returns flowOf(null)
            coEvery { notificationPermissionRequested(testUserId) } returns false
            coEvery { recordNotificationPrepromptShown(testUserId) } returns Unit
        }
        val permissionState = mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns false
        }
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = permissionState,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )

        controller.onLoginObserved()
        advanceUntilStateSettles()
        controller.dismiss()

        controller.onEngagementObserved()
        advanceUntilStateSettles()

        assertTrue(controller.uiState.value.visible)
    }

    @Test
    fun `dismissing after onShown keeps the per-process check claimed`() = runTest {
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { userId } returns flowOf(testUserId)
            every { notificationPrepromptShownCount(testUserId) } returns flowOf(0)
            every { notificationPrepromptLastShownAt(testUserId) } returns flowOf(null)
            coEvery { notificationPermissionRequested(testUserId) } returns false
            coEvery { recordNotificationPrepromptShown(testUserId) } returns Unit
        }
        val permissionState = mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns false
        }
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = permissionState,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )

        controller.onLoginObserved()
        advanceUntilStateSettles()
        controller.onShown()
        advanceUntilStateSettles()
        controller.dismiss()

        controller.onEngagementObserved()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    @Test
    fun `onLoginObserved is a no-op once shown MAX_SHOWN_COUNT times`() = runTest {
        val controller = buildController(shownCount = 3)

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    @Test
    fun `onLoginObserved is a no-op during the reshow cooldown`() = runTest {
        val controller = buildController(shownCount = 1, lastShownAt = fixedNow.minus(Duration.ofDays(2)))

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    @Test
    fun `onLoginObserved shows again once the cooldown has elapsed`() = runTest {
        val controller = buildController(shownCount = 1, lastShownAt = fixedNow.minus(Duration.ofDays(8)))

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertTrue(controller.uiState.value.visible)
    }

    @Test
    fun `onLoginObserved is a no-op if notifications are already enabled`() = runTest {
        val controller = buildController(notificationsAlreadyEnabled = true)

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    // ── pas 3.3 — the likes channel being individually blocked keeps the D card eligible ────

    @Test
    fun `onLoginObserved shows the card when notifications are enabled but the likes channel is blocked`() = runTest {
        val controller = buildController(notificationsAlreadyEnabled = true, likesChannelBlocked = true)

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertTrue(controller.uiState.value.visible)
    }

    @Test
    fun `onLoginObserved is still a no-op when notifications are enabled and the likes channel is not blocked`() = runTest {
        val controller = buildController(notificationsAlreadyEnabled = true, likesChannelBlocked = false)

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    @Test
    fun `onEngagementObserved after onLoginObserved does not double-trigger the same session`() = runTest {
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { userId } returns flowOf(testUserId)
            every { notificationPrepromptShownCount(testUserId) } returns flowOf(0)
            every { notificationPrepromptLastShownAt(testUserId) } returns flowOf(null)
            coEvery { notificationPermissionRequested(testUserId) } returns false
            coEvery { recordNotificationPrepromptShown(testUserId) } returns Unit
        }
        val permissionState = mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns false
        }
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = permissionState,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )

        controller.onLoginObserved()
        Thread.sleep(50)
        controller.onShown()
        controller.onEngagementObserved()
        Thread.sleep(50)

        coVerify(exactly = 1) { prefs.recordNotificationPrepromptShown(testUserId) }
    }

    private fun advanceUntilStateSettles() {
        // The controller launches on its own internal scope (Dispatchers.Default), not the test
        // dispatcher — runTest's virtual-time delay() doesn't wait for it, so a real (short) wait
        // is simplest here, same as the double-trigger test below.
        Thread.sleep(50)
    }

    // ── pas 1.3 — isEligibleForCampaign gate for onSessionRestored ─────────────────────────

    /** Mirrors the production `CAMPAIGN_COLD_START_GRACE` in NotificationPrepromptController.kt. */
    private val campaignColdStartGrace: Duration = Duration.ofSeconds(3)

    /** [Clock] whose [instant] can be advanced mid-test — needed to exercise the cold-start grace, where construction time and read time must differ. */
    private class MutableClock(
        private var current: Instant,
        private val zoneId: java.time.ZoneId = ZoneOffset.UTC,
    ) : Clock() {
        override fun getZone(): java.time.ZoneId = zoneId
        override fun withZone(zone: java.time.ZoneId): Clock = MutableClock(current, zone)
        override fun instant(): Instant = current
        fun advanceTo(instant: Instant) {
            current = instant
        }
    }

    private fun campaignPrefs(
        campaignDone: Boolean = false,
        onboardingDone: Boolean = true,
    ): UserPreferences = mockk<UserPreferences>(relaxed = true).apply {
        every { userId } returns flowOf(testUserId)
        every { notificationCampaignV1Done(testUserId) } returns flowOf(campaignDone)
        every { onboardingCompleted } returns flowOf(onboardingDone)
        coEvery { notificationPermissionRequested(testUserId) } returns false
        // Needed by onShown (step 0.2's showIndex read) whenever a test carries a campaign show
        // through to onShown — unused by isEligibleForCampaign itself.
        every { notificationPrepromptShownCount(testUserId) } returns flowOf(0)
    }

    private fun campaignPermissionState(notificationsAlreadyEnabled: Boolean = false): NotificationPermissionState =
        mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns notificationsAlreadyEnabled
        }

    @Test
    fun `onSessionRestored shows the card once every campaign condition is met`() = runTest {
        val clock = MutableClock(fixedNow)
        val controller = NotificationPrepromptController(
            userPreferences = campaignPrefs(),
            permissionState = campaignPermissionState(),
            clock = clock,
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )
        clock.advanceTo(fixedNow.plus(campaignColdStartGrace))

        controller.onSessionRestored()
        advanceUntilStateSettles()

        assertTrue(controller.uiState.value.visible)
    }

    @Test
    fun `onSessionRestored is a no-op if notifications are already enabled`() = runTest {
        val clock = MutableClock(fixedNow.plus(campaignColdStartGrace))
        val controller = NotificationPrepromptController(
            userPreferences = campaignPrefs(),
            permissionState = campaignPermissionState(notificationsAlreadyEnabled = true),
            clock = clock,
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )

        controller.onSessionRestored()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    @Test
    fun `onSessionRestored is a no-op if the campaign was already shown to this user`() = runTest {
        val clock = MutableClock(fixedNow.plus(campaignColdStartGrace))
        val controller = NotificationPrepromptController(
            userPreferences = campaignPrefs(campaignDone = true),
            permissionState = campaignPermissionState(),
            clock = clock,
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )

        controller.onSessionRestored()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    @Test
    fun `onSessionRestored is a no-op if onboarding is not complete`() = runTest {
        val clock = MutableClock(fixedNow.plus(campaignColdStartGrace))
        val controller = NotificationPrepromptController(
            userPreferences = campaignPrefs(onboardingDone = false),
            permissionState = campaignPermissionState(),
            clock = clock,
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )

        controller.onSessionRestored()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    @Test
    fun `onSessionRestored is a no-op while another app overlay is active`() = runTest {
        val clock = MutableClock(fixedNow.plus(campaignColdStartGrace))
        val overlayCoordinator = AppOverlayCoordinator().apply { setActive(ActiveOverlay.Tour, true) }
        val controller = NotificationPrepromptController(
            userPreferences = campaignPrefs(),
            permissionState = campaignPermissionState(),
            clock = clock,
            appOverlayCoordinator = overlayCoordinator,
            analyticsClient = null,
        )

        controller.onSessionRestored()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    @Test
    fun `onSessionRestored is a no-op while still within the cold-start grace period`() = runTest {
        // No advanceTo — the read happens at the same instant the controller was constructed.
        val clock = MutableClock(fixedNow)
        val controller = NotificationPrepromptController(
            userPreferences = campaignPrefs(),
            permissionState = campaignPermissionState(),
            clock = clock,
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )

        controller.onSessionRestored()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    // ── pas 1.4 — deferral when the tour is armed; consumed marking only on an actual show ──

    @Test
    fun `onShown marks the campaign consumed for the upgrade_campaign surface`() = runTest {
        val prefs = campaignPrefs()
        val clock = MutableClock(fixedNow)
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = campaignPermissionState(),
            clock = clock,
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )
        clock.advanceTo(fixedNow.plus(campaignColdStartGrace))

        controller.onSessionRestored()
        advanceUntilStateSettles()
        controller.onShown()
        advanceUntilStateSettles()

        coVerify(exactly = 1) { prefs.setNotificationCampaignV1Done(testUserId) }
    }

    @Test
    fun `onShown does not mark the campaign consumed for the login surface`() = runTest {
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { userId } returns flowOf(testUserId)
            every { notificationPrepromptShownCount(testUserId) } returns flowOf(0)
            every { notificationPrepromptLastShownAt(testUserId) } returns flowOf(null)
            coEvery { notificationPermissionRequested(testUserId) } returns false
            coEvery { recordNotificationPrepromptShown(testUserId) } returns Unit
        }
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = campaignPermissionState(),
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )

        controller.onLoginObserved()
        advanceUntilStateSettles()
        controller.onShown()
        advanceUntilStateSettles()

        coVerify(exactly = 0) { prefs.setNotificationCampaignV1Done(any()) }
    }

    @Test
    fun `onSessionRestored defers while the tour is armed and never marks the campaign consumed`() = runTest {
        val prefs = campaignPrefs()
        val clock = MutableClock(fixedNow)
        val overlayCoordinator = AppOverlayCoordinator().apply { setActive(ActiveOverlay.Tour, true) }
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = campaignPermissionState(),
            clock = clock,
            appOverlayCoordinator = overlayCoordinator,
            analyticsClient = null,
        )
        clock.advanceTo(fixedNow.plus(campaignColdStartGrace))

        controller.onSessionRestored()
        advanceUntilStateSettles()
        controller.onShown() // no-op: pendingShow was never set, since eligibility failed
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
        coVerify(exactly = 0) { prefs.setNotificationCampaignV1Done(testUserId) }
    }

    @Test
    fun `a later session without the tour shows the campaign since it was never marked consumed`() = runTest {
        // First session: the tour is armed/active, so the campaign is deferred and left unmarked.
        val firstSessionPrefs = campaignPrefs()
        val firstSessionClock = MutableClock(fixedNow)
        val firstSessionOverlayCoordinator = AppOverlayCoordinator().apply { setActive(ActiveOverlay.Tour, true) }
        val firstSessionController = NotificationPrepromptController(
            userPreferences = firstSessionPrefs,
            permissionState = campaignPermissionState(),
            clock = firstSessionClock,
            appOverlayCoordinator = firstSessionOverlayCoordinator,
            analyticsClient = null,
        )
        firstSessionClock.advanceTo(fixedNow.plus(campaignColdStartGrace))

        firstSessionController.onSessionRestored()
        advanceUntilStateSettles()

        assertFalse(firstSessionController.uiState.value.visible)
        coVerify(exactly = 0) { firstSessionPrefs.setNotificationCampaignV1Done(testUserId) }

        // Next session (fresh process): campaign still not done, tour no longer active.
        val secondSessionClock = MutableClock(fixedNow)
        val secondSessionController = NotificationPrepromptController(
            userPreferences = campaignPrefs(), // campaignDone defaults to false — still unconsumed
            permissionState = campaignPermissionState(),
            clock = secondSessionClock,
            appOverlayCoordinator = AppOverlayCoordinator(), // no tour active this time
            analyticsClient = null,
        )
        secondSessionClock.advanceTo(fixedNow.plus(campaignColdStartGrace))

        secondSessionController.onSessionRestored()
        advanceUntilStateSettles()

        assertTrue(secondSessionController.uiState.value.visible)
    }

    // ── pas 2.1 — participation in AppOverlayCoordinator ────────────────────────────────────

    @Test
    fun `onLoginObserved reports NotificationPreprompt active to the overlay coordinator while visible`() = runTest {
        val overlayCoordinator = AppOverlayCoordinator()
        val controller = buildController(appOverlayCoordinator = overlayCoordinator)

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertTrue(controller.uiState.value.visible)
        assertEquals(ActiveOverlay.NotificationPreprompt, overlayCoordinator.activeOverlay.value)
    }

    @Test
    fun `dismiss reports NotificationPreprompt inactive to the overlay coordinator`() = runTest {
        val overlayCoordinator = AppOverlayCoordinator()
        val controller = buildController(appOverlayCoordinator = overlayCoordinator)

        controller.onLoginObserved()
        advanceUntilStateSettles()
        controller.dismiss()

        assertNull(overlayCoordinator.activeOverlay.value)
    }

    @Test
    fun `close reports NotificationPreprompt inactive to the overlay coordinator`() = runTest {
        val overlayCoordinator = AppOverlayCoordinator()
        val controller = buildController(appOverlayCoordinator = overlayCoordinator)

        controller.onLoginObserved()
        advanceUntilStateSettles()
        controller.close()

        assertNull(overlayCoordinator.activeOverlay.value)
    }

    @Test
    fun `onLoginObserved does not show while a higher-priority overlay is active`() = runTest {
        val overlayCoordinator = AppOverlayCoordinator().apply { setActive(ActiveOverlay.Tour, true) }
        val controller = buildController(appOverlayCoordinator = overlayCoordinator)

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    @Test
    fun `onEngagementObserved does not show while a higher-priority overlay is active`() = runTest {
        val overlayCoordinator = AppOverlayCoordinator().apply { setActive(ActiveOverlay.FirstPostFeedback, true) }
        val controller = buildController(appOverlayCoordinator = overlayCoordinator)

        controller.onEngagementObserved()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    // ── pas 2.2 — a blocking moderation notice blocks the pre-prompt, never the reverse ────

    @Test
    fun `onLoginObserved does not show while a moderation notice is active`() = runTest {
        val overlayCoordinator = AppOverlayCoordinator().apply { setActive(ActiveOverlay.Moderation, true) }
        val controller = buildController(appOverlayCoordinator = overlayCoordinator)

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertFalse(controller.uiState.value.visible)
    }

    @Test
    fun `the pre-prompt being active never blocks a moderation notice`() = runTest {
        val overlayCoordinator = AppOverlayCoordinator()
        val controller = buildController(appOverlayCoordinator = overlayCoordinator)

        controller.onLoginObserved()
        advanceUntilStateSettles()

        assertTrue(controller.uiState.value.visible)
        assertFalse(overlayCoordinator.isBlockedBy(ActiveOverlay.Moderation))
    }

    // ── pas 3.4 — ON_RESUME re-evaluation ───────────────────────────────────────────────────

    @Test
    fun `onResumed is a no-op when the card is not visible`() = runTest {
        val permissionState = mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns true
        }
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { userId } returns flowOf(testUserId)
        }
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = permissionState,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )

        controller.onResumed()

        assertFalse(controller.uiState.value.visible)
        coVerify(exactly = 0) { prefs.setNotificationCampaignV1Done(any()) }
    }

    @Test
    fun `onResumed keeps the card visible while notifications are still not enabled`() = runTest {
        val permissionState = mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns false
        }
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { userId } returns flowOf(testUserId)
            every { notificationPrepromptShownCount(testUserId) } returns flowOf(0)
            every { notificationPrepromptLastShownAt(testUserId) } returns flowOf(null)
            coEvery { notificationPermissionRequested(testUserId) } returns false
        }
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = permissionState,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )
        controller.onLoginObserved()
        advanceUntilStateSettles()
        assertTrue(controller.uiState.value.visible)

        controller.onResumed()

        assertTrue(controller.uiState.value.visible)
        coVerify(exactly = 0) { prefs.setNotificationCampaignV1Done(any()) }
    }

    @Test
    fun `onResumed closes the card and marks the campaign succeeded once notifications become enabled`() = runTest {
        val permissionState = mockk<NotificationPermissionState>(relaxed = true).apply {
            every { areNotificationsEnabled() } returns false
        }
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { userId } returns flowOf(testUserId)
            every { notificationPrepromptShownCount(testUserId) } returns flowOf(0)
            every { notificationPrepromptLastShownAt(testUserId) } returns flowOf(null)
            coEvery { notificationPermissionRequested(testUserId) } returns false
        }
        val controller = NotificationPrepromptController(
            userPreferences = prefs,
            permissionState = permissionState,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = null,
        )
        controller.onLoginObserved()
        advanceUntilStateSettles()
        assertTrue(controller.uiState.value.visible)

        // Simulates a trip to Settings that granted the permission out-of-band.
        every { permissionState.areNotificationsEnabled() } returns true
        controller.onResumed()

        assertFalse(controller.uiState.value.visible)
        coVerify(exactly = 1) { prefs.setNotificationCampaignV1Done(testUserId) }
    }

    // ── pas 5.1 — surface="upgrade_campaign" flows into every campaign-triggered event ──────

    @Test
    fun `onSessionRestored logs push_preprompt_shown with surface upgrade_campaign`() = runTest {
        val analyticsClient = mockk<AnalyticsClient>(relaxed = true)
        val clock = MutableClock(fixedNow)
        val controller = NotificationPrepromptController(
            userPreferences = campaignPrefs(),
            permissionState = campaignPermissionState(),
            clock = clock,
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = analyticsClient,
        )
        clock.advanceTo(fixedNow.plus(campaignColdStartGrace))

        controller.onSessionRestored()
        advanceUntilStateSettles()
        controller.onShown()
        advanceUntilStateSettles()

        verify(exactly = 1) {
            analyticsClient.log(match { it.name == "push_preprompt_shown" && (it.params["surface"] as? com.revio.social.core.analytics.AnalyticsParamValue.StringValue)?.value == "upgrade_campaign" })
        }
    }

    @Test
    fun `campaign accept, permission result, settings-opened, and dismiss events all carry surface upgrade_campaign`() = runTest {
        val analyticsClient = mockk<AnalyticsClient>(relaxed = true)
        val clock = MutableClock(fixedNow)
        val controller = NotificationPrepromptController(
            userPreferences = campaignPrefs(),
            permissionState = campaignPermissionState(),
            clock = clock,
            appOverlayCoordinator = AppOverlayCoordinator(),
            analyticsClient = analyticsClient,
        )
        clock.advanceTo(fixedNow.plus(campaignColdStartGrace))
        controller.onSessionRestored()
        advanceUntilStateSettles()
        controller.onShown()
        advanceUntilStateSettles()

        controller.onAccepted()
        controller.onPermissionRequested(granted = true)
        controller.logSettingsOpened()
        controller.dismiss()

        fun surfaceOf(eventName: String): String? {
            val slot = mutableListOf<AnalyticsEvent>()
            verify { analyticsClient.log(capture(slot)) }
            return slot.firstOrNull { it.name == eventName }
                ?.params?.get("surface")
                ?.let { (it as? com.revio.social.core.analytics.AnalyticsParamValue.StringValue)?.value }
        }

        assertEquals("upgrade_campaign", surfaceOf("push_preprompt_result"))
        assertEquals("upgrade_campaign", surfaceOf("push_permission_requested"))
        assertEquals("upgrade_campaign", surfaceOf("push_permission_result"))
        assertEquals("upgrade_campaign", surfaceOf("push_settings_opened"))
    }
}
