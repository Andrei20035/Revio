package com.revio.social.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [resolvePermissionStatus] — the pure derivation behind step 3.2. */
class NotificationPermissionStatusTest {

    private data class Case(
        val hasPostNotificationsPermission: Boolean,
        val notificationsEnabled: Boolean,
        val shouldShowRequestPermissionRationale: Boolean,
        val everRequested: Boolean,
        val expected: NotificationPermissionStatus,
    )

    /**
     * Every one of the 16 combinations of the 4 boolean inputs, most-significant input first
     * (permission, enabled, rationale, everRequested) — see [resolvePermissionStatus]'s own KDoc
     * for the reasoning behind each branch.
     */
    private val cases = listOf(
        // GRANTED requires both permission and the app-wide toggle — everything else is then irrelevant.
        Case(hasPostNotificationsPermission = true, notificationsEnabled = true, shouldShowRequestPermissionRationale = true, everRequested = true, expected = NotificationPermissionStatus.GRANTED),
        Case(hasPostNotificationsPermission = true, notificationsEnabled = true, shouldShowRequestPermissionRationale = true, everRequested = false, expected = NotificationPermissionStatus.GRANTED),
        Case(hasPostNotificationsPermission = true, notificationsEnabled = true, shouldShowRequestPermissionRationale = false, everRequested = true, expected = NotificationPermissionStatus.GRANTED),
        Case(hasPostNotificationsPermission = true, notificationsEnabled = true, shouldShowRequestPermissionRationale = false, everRequested = false, expected = NotificationPermissionStatus.GRANTED),

        // Permission granted but the app-wide toggle is off: treated as PERMANENTLY_DENIED — only
        // Settings can fix it, re-requesting an already-granted permission is a silent no-op.
        Case(hasPostNotificationsPermission = true, notificationsEnabled = false, shouldShowRequestPermissionRationale = true, everRequested = true, expected = NotificationPermissionStatus.PERMANENTLY_DENIED),
        Case(hasPostNotificationsPermission = true, notificationsEnabled = false, shouldShowRequestPermissionRationale = true, everRequested = false, expected = NotificationPermissionStatus.PERMANENTLY_DENIED),
        Case(hasPostNotificationsPermission = true, notificationsEnabled = false, shouldShowRequestPermissionRationale = false, everRequested = true, expected = NotificationPermissionStatus.PERMANENTLY_DENIED),
        Case(hasPostNotificationsPermission = true, notificationsEnabled = false, shouldShowRequestPermissionRationale = false, everRequested = false, expected = NotificationPermissionStatus.PERMANENTLY_DENIED),

        // No permission, rationale true: denied once but not permanently — the dialog can still be shown.
        Case(hasPostNotificationsPermission = false, notificationsEnabled = true, shouldShowRequestPermissionRationale = true, everRequested = true, expected = NotificationPermissionStatus.DENIED_ONCE),
        Case(hasPostNotificationsPermission = false, notificationsEnabled = true, shouldShowRequestPermissionRationale = true, everRequested = false, expected = NotificationPermissionStatus.DENIED_ONCE),
        Case(hasPostNotificationsPermission = false, notificationsEnabled = false, shouldShowRequestPermissionRationale = true, everRequested = true, expected = NotificationPermissionStatus.DENIED_ONCE),
        Case(hasPostNotificationsPermission = false, notificationsEnabled = false, shouldShowRequestPermissionRationale = true, everRequested = false, expected = NotificationPermissionStatus.DENIED_ONCE),

        // No permission, rationale false: the genuinely ambiguous case — everRequested disambiguates.
        Case(hasPostNotificationsPermission = false, notificationsEnabled = true, shouldShowRequestPermissionRationale = false, everRequested = true, expected = NotificationPermissionStatus.PERMANENTLY_DENIED),
        Case(hasPostNotificationsPermission = false, notificationsEnabled = true, shouldShowRequestPermissionRationale = false, everRequested = false, expected = NotificationPermissionStatus.NEVER_ASKED),
        Case(hasPostNotificationsPermission = false, notificationsEnabled = false, shouldShowRequestPermissionRationale = false, everRequested = true, expected = NotificationPermissionStatus.PERMANENTLY_DENIED),
        Case(hasPostNotificationsPermission = false, notificationsEnabled = false, shouldShowRequestPermissionRationale = false, everRequested = false, expected = NotificationPermissionStatus.NEVER_ASKED),
    )

    @Test
    fun `resolvePermissionStatus matches the full truth table`() {
        cases.forEach { case ->
            val actual = resolvePermissionStatus(
                hasPostNotificationsPermission = case.hasPostNotificationsPermission,
                notificationsEnabled = case.notificationsEnabled,
                shouldShowRequestPermissionRationale = case.shouldShowRequestPermissionRationale,
                everRequested = case.everRequested,
            )
            assertEquals(case.toString(), case.expected, actual)
        }
    }
}
