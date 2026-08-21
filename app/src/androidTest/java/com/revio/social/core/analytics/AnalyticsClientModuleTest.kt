package com.revio.social.core.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pas 1.3b: instrumented tests always run against the debug build type (AGP default, no
 * `testBuildType` override in this module) — so the injected [AnalyticsClient] here must be the
 * no-op one, never [FirebaseAnalyticsClient]. That's the only way to assert "zero events reach
 * the real Firebase project" from a JVM-less test: prove the binding itself, not a network call.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AnalyticsClientModuleTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var analyticsClient: AnalyticsClient

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun debugBuildInjectsTheNoOpAnalyticsClient() {
        assertTrue(analyticsClient is NoOpAnalyticsClient)
    }
}
