package com.revio.social.core.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class CrashContextTest {

    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)

    @Before
    fun setUp() {
        CrashContext.resetBudgetForTests()
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    @Test
    fun `no custom key is a forbidden identifier`() {
        val forbiddenIdentifierKeys = setOf("user_id", "post_id", "credential_id", "device_id")

        assertFalse(CrashContext.CUSTOM_KEYS.any { it in forbiddenIdentifierKeys })
    }

    @Test
    fun `each declared custom key is settable and reaches Crashlytics`() {
        CrashContext.setFlow("auth")
        CrashContext.setStage("login")
        CrashContext.setLastApiCode("EMAIL_TAKEN")
        CrashContext.setOffline(true)
        CrashContext.setBuildType("debug")

        verify { crashlytics.setCustomKey("flow", "auth") }
        verify { crashlytics.setCustomKey("stage", "login") }
        verify { crashlytics.setCustomKey("last_api_code", "EMAIL_TAKEN") }
        verify { crashlytics.setCustomKey("is_offline", true) }
        verify { crashlytics.setCustomKey("build_type", "debug") }
    }

    @Test
    fun `breadcrumb within budget reaches Crashlytics`() {
        CrashContext.breadcrumb("short message")

        verify { crashlytics.log("short message") }
    }

    @Test
    fun `breadcrumb exceeding the remaining budget is dropped, not sent`() {
        val fillsTheBudget = "x".repeat(BREADCRUMB_BUDGET_BYTES)
        CrashContext.breadcrumb(fillsTheBudget)
        CrashContext.breadcrumb("one more breadcrumb")

        verify(exactly = 1) { crashlytics.log(any()) }
    }
}
