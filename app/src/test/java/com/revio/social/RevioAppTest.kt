package com.revio.social

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.revio.social.data.local.preferences.UserPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Opt-out consent (docs/consent-decision.md) as applied at cold start. [RevioApp.onCreate] used to
 * hardcode collection off, which re-revoked a granted consent on every launch; it now applies the
 * value persisted in UserPreferences, so both branches matter here — not just the default-on one.
 */
class RevioAppTest {

    private val context = mockk<Context>(relaxed = true)
    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
    private val firebaseAnalytics = mockk<FirebaseAnalytics>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        mockkStatic(FirebaseAnalytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        every { FirebaseAnalytics.getInstance(any()) } returns firebaseAnalytics
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
        unmockkStatic(FirebaseAnalytics::class)
    }

    @Test
    fun `no consent keeps both collections off`() {
        applyAnalyticsConsent(context, granted = false)

        verify(exactly = 1) { crashlytics.setCrashlyticsCollectionEnabled(false) }
        verify(exactly = 1) { firebaseAnalytics.setAnalyticsCollectionEnabled(false) }
    }

    @Test
    fun `granted consent turns both collections on — the cold-start regression this covers`() {
        applyAnalyticsConsent(context, granted = true)

        verify(exactly = 1) { crashlytics.setCrashlyticsCollectionEnabled(true) }
        verify(exactly = 1) { firebaseAnalytics.setAnalyticsCollectionEnabled(true) }
    }

    @Test
    fun `cold start with no persisted preference turns both collections on`() = runBlocking {
        val userPreferences = mockk<UserPreferences> {
            every { analyticsConsentGranted } returns flowOf(true)
        }

        applyAnalyticsConsent(context, userPreferences.analyticsConsentGranted.first())

        verify(exactly = 1) { crashlytics.setCrashlyticsCollectionEnabled(true) }
        verify(exactly = 1) { firebaseAnalytics.setAnalyticsCollectionEnabled(true) }
    }

    @Test
    fun `cold start with false persisted preference keeps both collections off`() = runBlocking {
        val userPreferences = mockk<UserPreferences> {
            every { analyticsConsentGranted } returns flowOf(false)
        }

        applyAnalyticsConsent(context, userPreferences.analyticsConsentGranted.first())

        verify(exactly = 1) { crashlytics.setCrashlyticsCollectionEnabled(false) }
        verify(exactly = 1) { firebaseAnalytics.setAnalyticsCollectionEnabled(false) }
    }
}
