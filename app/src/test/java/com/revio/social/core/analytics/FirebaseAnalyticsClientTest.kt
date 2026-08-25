package com.revio.social.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test

class FirebaseAnalyticsClientTest {

    private val firebaseAnalytics = mockk<FirebaseAnalytics>(relaxed = true)
    private val client = FirebaseAnalyticsClient(firebaseAnalytics)

    @After
    fun tearDown() {
        unmockkConstructor(Bundle::class)
    }

    @Test
    fun `log forwards the event name to FirebaseAnalytics`() {
        client.log(AnalyticsEvent(name = "post_upload_result"))

        verify { firebaseAnalytics.logEvent("post_upload_result", any()) }
    }

    @Test
    fun `log puts each param into the bundle by its type`() {
        mockkConstructor(Bundle::class)
        // mockkConstructor makes every `Bundle()` call (including this one) return the same
        // mocked singleton instance that FirebaseAnalyticsClient.log() will build internally.
        val bundle = Bundle()
        every { bundle.putString(any(), any()) } just Runs
        every { bundle.putLong(any(), any()) } just Runs
        every { bundle.putDouble(any(), any()) } just Runs

        client.log(
            AnalyticsEvent(
                name = "post_upload_result",
                params = mapOf(
                    "outcome" to AnalyticsParamValue.StringValue("success"),
                    "attempt_count" to AnalyticsParamValue.LongValue(0L),
                    "duration_seconds" to AnalyticsParamValue.DoubleValue(1.5),
                ),
            ),
        )

        verify { bundle.putString("outcome", "success") }
        verify { bundle.putLong("attempt_count", 0L) }
        verify { bundle.putDouble("duration_seconds", 1.5) }
        verify { firebaseAnalytics.logEvent("post_upload_result", any()) }
    }

    @Test
    fun `event with a forbidden key like post_id never reaches FirebaseAnalytics`() {
        // BuildConfig.DEBUG is true for unit tests, so AnalyticsSanitizer runs in strict mode here
        // and throws rather than silently dropping the key — see AnalyticsSanitizer's KDoc.
        assertThrows(AnalyticsValidationException::class.java) {
            client.log(
                AnalyticsEvent(
                    name = "post_upload_result",
                    params = mapOf("post_id" to AnalyticsParamValue.StringValue("abc-123")),
                ),
            )
        }

        verify(exactly = 0) { firebaseAnalytics.logEvent(any(), any()) }
    }
}
