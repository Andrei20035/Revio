package com.revio.social.core.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 2.1 — [ActiveOverlay.NotificationPreprompt] joins the priority list as the lowest
 * priority (last), below [ActiveOverlay.FirstPostFeedback]. Step 2.2 —
 * [ActiveOverlay.Moderation] joins as the highest priority (first): it must always block
 * everything else, and never be blocked itself.
 */
class AppOverlayCoordinatorTest {

    @Test
    fun `nothing is active and nothing is blocked by default`() {
        val coordinator = AppOverlayCoordinator()

        assertNull(coordinator.activeOverlay.value)
        ActiveOverlay.entries.forEach { overlay -> assertFalse(coordinator.isBlockedBy(overlay)) }
    }

    @Test
    fun `NotificationPreprompt is blocked by every higher-priority overlay`() {
        val coordinator = AppOverlayCoordinator()

        coordinator.setActive(ActiveOverlay.Tour, true)
        assertTrue(coordinator.isBlockedBy(ActiveOverlay.NotificationPreprompt))
        coordinator.setActive(ActiveOverlay.Tour, false)

        coordinator.setActive(ActiveOverlay.EarlySpotter, true)
        assertTrue(coordinator.isBlockedBy(ActiveOverlay.NotificationPreprompt))
        coordinator.setActive(ActiveOverlay.EarlySpotter, false)

        coordinator.setActive(ActiveOverlay.FirstPostFeedback, true)
        assertTrue(coordinator.isBlockedBy(ActiveOverlay.NotificationPreprompt))
    }

    @Test
    fun `NotificationPreprompt is not blocked by itself and can become the active overlay`() {
        val coordinator = AppOverlayCoordinator()

        coordinator.setActive(ActiveOverlay.NotificationPreprompt, true)

        assertEquals(ActiveOverlay.NotificationPreprompt, coordinator.activeOverlay.value)
        assertFalse(coordinator.isBlockedBy(ActiveOverlay.NotificationPreprompt))
    }

    @Test
    fun `NotificationPreprompt never blocks a higher-priority overlay`() {
        val coordinator = AppOverlayCoordinator()

        coordinator.setActive(ActiveOverlay.NotificationPreprompt, true)

        assertFalse(coordinator.isBlockedBy(ActiveOverlay.Tour))
        assertFalse(coordinator.isBlockedBy(ActiveOverlay.EarlySpotter))
        assertFalse(coordinator.isBlockedBy(ActiveOverlay.FirstPostFeedback))
    }

    @Test
    fun `deactivating NotificationPreprompt clears the active overlay`() {
        val coordinator = AppOverlayCoordinator()
        coordinator.setActive(ActiveOverlay.NotificationPreprompt, true)

        coordinator.setActive(ActiveOverlay.NotificationPreprompt, false)

        assertNull(coordinator.activeOverlay.value)
    }

    // ── pas 2.2 — Moderation, highest priority ───────────────────────────────────────────

    @Test
    fun `Moderation blocks every other overlay`() {
        val coordinator = AppOverlayCoordinator()
        coordinator.setActive(ActiveOverlay.Moderation, true)

        ActiveOverlay.entries.filter { it != ActiveOverlay.Moderation }.forEach { overlay ->
            assertTrue(coordinator.isBlockedBy(overlay))
        }
    }

    @Test
    fun `Moderation is never blocked by anything else`() {
        val coordinator = AppOverlayCoordinator()

        ActiveOverlay.entries.filter { it != ActiveOverlay.Moderation }.forEach { overlay ->
            coordinator.setActive(overlay, true)
            assertFalse(coordinator.isBlockedBy(ActiveOverlay.Moderation))
            coordinator.setActive(overlay, false)
        }
    }

    @Test
    fun `Moderation becomes the active overlay over an already-active tour`() {
        val coordinator = AppOverlayCoordinator()
        coordinator.setActive(ActiveOverlay.Tour, true)

        coordinator.setActive(ActiveOverlay.Moderation, true)

        assertEquals(ActiveOverlay.Moderation, coordinator.activeOverlay.value)
    }
}
