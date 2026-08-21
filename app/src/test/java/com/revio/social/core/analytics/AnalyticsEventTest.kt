package com.revio.social.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These types carry no logic yet (validation lands in a later step) — this only pins down
 * that construction works and params round-trip as given.
 */
class AnalyticsEventTest {

    @Test
    fun `event with no params defaults to an empty map`() {
        val event = AnalyticsEvent(name = "post_create_start")

        assertEquals("post_create_start", event.name)
        assertTrue(event.params.isEmpty())
    }

    @Test
    fun `event carries the params it was constructed with`() {
        val event = AnalyticsEvent(
            name = "post_upload_result",
            params = mapOf(
                "outcome" to AnalyticsParamValue.StringValue("success"),
                "retry_bucket" to AnalyticsParamValue.LongValue(0L),
                "duration_seconds" to AnalyticsParamValue.DoubleValue(1.5),
            ),
        )

        assertEquals(AnalyticsParamValue.StringValue("success"), event.params["outcome"])
        assertEquals(AnalyticsParamValue.LongValue(0L), event.params["retry_bucket"])
        assertEquals(AnalyticsParamValue.DoubleValue(1.5), event.params["duration_seconds"])
    }

    private fun logsSomewhere(client: AnalyticsClient) = client.log(AnalyticsEvent(name = "app_start_resolved"))

    @Test
    fun `AnalyticsClient is implementable`() {
        var lastLogged: AnalyticsEvent? = null
        val client = object : AnalyticsClient {
            override fun log(event: AnalyticsEvent) {
                lastLogged = event
            }
        }

        logsSomewhere(client)

        assertEquals("app_start_resolved", lastLogged?.name)
    }
}
