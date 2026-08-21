package com.revio.social.core.analytics

import org.junit.Test

class NoOpAnalyticsClientTest {

    @Test
    fun `log does nothing and never throws`() {
        val client = NoOpAnalyticsClient()

        client.log(
            AnalyticsEvent(
                name = "post_upload_result",
                params = mapOf("outcome" to AnalyticsParamValue.StringValue("success")),
            ),
        )
    }
}
