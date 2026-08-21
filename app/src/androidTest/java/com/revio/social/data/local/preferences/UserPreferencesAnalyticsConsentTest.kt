package com.revio.social.data.local.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in consent (docs/consent-decision.md): [UserPreferences.analyticsConsentGranted] is backed
 * by DataStore, which needs a real Android [android.content.Context] — hence instrumented, not
 * a JVM unit test.
 */
@RunWith(AndroidJUnit4::class)
class UserPreferencesAnalyticsConsentTest {

    private val userPreferences = UserPreferences(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        json = Json,
    )

    @Test
    fun analyticsConsentRoundTripsThroughDataStore() = runBlocking {
        userPreferences.setAnalyticsConsentGranted(true)
        assertTrue(userPreferences.analyticsConsentGranted.first())

        userPreferences.setAnalyticsConsentGranted(false)
        assertFalse(userPreferences.analyticsConsentGranted.first())
    }
}
