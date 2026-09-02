package com.revio.social.core.notifications

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [resolveNotifyMeAction] — the pure CTA-routing decision behind step 0.3. */
class NotificationPrepromptCtaActionTest {

    @Test
    fun `permission previously requested always opens Settings, regardless of SDK level`() {
        assertEquals(
            NotificationPrepromptCtaAction.OPEN_SETTINGS,
            resolveNotifyMeAction(sdkInt = Build.VERSION_CODES.TIRAMISU, permissionPreviouslyRequested = true),
        )
        assertEquals(
            NotificationPrepromptCtaAction.OPEN_SETTINGS,
            resolveNotifyMeAction(sdkInt = Build.VERSION_CODES.O, permissionPreviouslyRequested = true),
        )
    }

    @Test
    fun `never requested on API 33+ requests the OS permission dialog`() {
        assertEquals(
            NotificationPrepromptCtaAction.REQUEST_PERMISSION,
            resolveNotifyMeAction(sdkInt = Build.VERSION_CODES.TIRAMISU, permissionPreviouslyRequested = false),
        )
    }

    @Test
    fun `never requested on API 26-32 opens Settings instead of a no-op`() {
        // API 26-32 has no POST_NOTIFICATIONS runtime permission to request at all, so the CTA
        // must fall through to Settings rather than merely closing the card (step 0.3).
        assertEquals(
            NotificationPrepromptCtaAction.OPEN_SETTINGS,
            resolveNotifyMeAction(sdkInt = Build.VERSION_CODES.O, permissionPreviouslyRequested = false),
        )
        assertEquals(
            NotificationPrepromptCtaAction.OPEN_SETTINGS,
            resolveNotifyMeAction(sdkInt = Build.VERSION_CODES.S_V2, permissionPreviouslyRequested = false),
        )
    }
}
