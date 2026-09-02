package com.revio.social.data.local.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [UserPreferences.notificationPermissionRequested] / [UserPreferences
 * .setNotificationPermissionRequested] (step 3.1) are backed by DataStore, which needs a real
 * Android [android.content.Context] — hence instrumented, not a JVM unit test (same reasoning as
 * [UserPreferencesAnalyticsConsentTest] / [UserPreferencesNotificationCampaignTest]). Per-user
 * like [UserPreferencesNotificationCampaignTest], so a freshly random id in each test reliably
 * observes the key in its untouched ("absent") state.
 *
 * Not covered here: the one-time migration from the pre-3.1 device-wide legacy key. That path is
 * only reachable through [UserPreferences]'s own private `context.dataStore` delegate, with no
 * safe way to seed the legacy key from a second, independent DataStore instance pointed at the
 * same file without risking DataStore's own multi-instance guard — the same structural gap
 * already exists, unaddressed, for [UserPreferences.tourStatus]'s identical migration shape.
 */
@RunWith(AndroidJUnit4::class)
class UserPreferencesNotificationPermissionRequestedTest {

    private val userPreferences = UserPreferences(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        json = Json,
    )

    @Test
    fun absentByDefault() = runBlocking {
        val userId = UUID.randomUUID()

        assertFalse(userPreferences.notificationPermissionRequested(userId))
    }

    @Test
    fun trueAfterBeingSet() = runBlocking {
        val userId = UUID.randomUUID()

        userPreferences.setNotificationPermissionRequested(userId)

        assertTrue(userPreferences.notificationPermissionRequested(userId))
    }

    @Test
    fun isolatedPerUser() = runBlocking {
        val requestedUserId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()

        userPreferences.setNotificationPermissionRequested(requestedUserId)

        assertTrue(userPreferences.notificationPermissionRequested(requestedUserId))
        // The second user starts from zero — they never inherit the first account's flag.
        assertFalse(userPreferences.notificationPermissionRequested(otherUserId))
    }
}
