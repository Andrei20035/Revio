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
 * Opt-out consent (docs/consent-decision.md): [UserPreferences.analyticsConsentGranted] is backed
 * by DataStore, which needs a real Android [android.content.Context] — hence instrumented, not
 * a JVM unit test. The "no key persisted yet" default (`?: true`) is exercised at the unit level
 * instead (RevioAppTest, SettingsViewModelTest, with a mocked flow) since this suite shares a
 * single app process/DataStore file with other instrumented tests and has no reliable way to
 * observe the key in an untouched state.
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
