package com.revio.social.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsSanitizerTest {

    // --- forbidden keys (PII) ---

    @Test
    fun `forbidden key throws in strict mode`() {
        val event = AnalyticsEvent(
            name = "auth_result",
            params = mapOf("email" to AnalyticsParamValue.StringValue("a@b.com")),
        )

        assertThrows(AnalyticsValidationException::class.java) {
            AnalyticsSanitizer.sanitize(event, strict = true)
        }
    }

    @Test
    fun `forbidden key is dropped in lenient mode, event still logs`() {
        val event = AnalyticsEvent(
            name = "auth_result",
            params = mapOf(
                "email" to AnalyticsParamValue.StringValue("a@b.com"),
                "outcome" to AnalyticsParamValue.StringValue("success"),
            ),
        )

        val sanitized = AnalyticsSanitizer.sanitize(event, strict = false)

        assertEquals("auth_result", sanitized.name)
        assertFalse(sanitized.params.containsKey("email"))
        assertEquals(AnalyticsParamValue.StringValue("success"), sanitized.params["outcome"])
    }

    @Test
    fun `every category from the forbidden list is rejected`() {
        val forbiddenKeys = listOf(
            "email", "phone", "phone_number", "username", "full_name", "name",
            "birth_date", "date_of_birth", "password",
            "token", "access_token", "refresh_token",
            "caption", "comment", "comment_text",
            "request_body", "response_body",
            "latitude", "longitude",
            "image_url", "image_path", "object_key",
            "user_id", "post_id", "credential_id", "device_id",
        )

        forbiddenKeys.forEach { key ->
            val event = AnalyticsEvent(name = "x", params = mapOf(key to AnalyticsParamValue.StringValue("v")))
            val sanitized = AnalyticsSanitizer.sanitize(event, strict = false)
            assertTrue("expected \"$key\" to be dropped", sanitized.params.isEmpty())
        }
    }

    // --- truncation ---

    @Test
    fun `oversized string value throws in strict mode`() {
        val event = AnalyticsEvent(
            name = "post_upload_result",
            params = mapOf("reason" to AnalyticsParamValue.StringValue("x".repeat(101))),
        )

        assertThrows(AnalyticsValidationException::class.java) {
            AnalyticsSanitizer.sanitize(event, strict = true)
        }
    }

    @Test
    fun `oversized string value is truncated to 100 chars in lenient mode`() {
        val event = AnalyticsEvent(
            name = "post_upload_result",
            params = mapOf("reason" to AnalyticsParamValue.StringValue("x".repeat(101))),
        )

        val sanitized = AnalyticsSanitizer.sanitize(event, strict = false)

        assertEquals(100, (sanitized.params["reason"] as AnalyticsParamValue.StringValue).value.length)
    }

    @Test
    fun `value at exactly 100 chars is not truncated`() {
        val exact = "x".repeat(100)
        val event = AnalyticsEvent(name = "post_upload_result", params = mapOf("reason" to AnalyticsParamValue.StringValue(exact)))

        val sanitized = AnalyticsSanitizer.sanitize(event, strict = true)

        assertEquals(exact, (sanitized.params["reason"] as AnalyticsParamValue.StringValue).value)
    }

    @Test
    fun `event name over 40 chars throws in strict and is truncated in lenient`() {
        val longName = "a".repeat(41)
        val event = AnalyticsEvent(name = longName)

        assertThrows(AnalyticsValidationException::class.java) {
            AnalyticsSanitizer.sanitize(event, strict = true)
        }
        val sanitized = AnalyticsSanitizer.sanitize(event, strict = false)
        assertEquals(40, sanitized.name.length)
    }

    // --- bucket values at their boundaries ---

    @Test
    fun `every declared duration_bucket value is accepted`() {
        listOf("lt_1s", "1_3s", "3_10s", "10_30s", "gte_30s").forEach { bucket ->
            val event = AnalyticsEvent(
                name = "post_upload_result",
                params = mapOf("duration_bucket" to AnalyticsParamValue.StringValue(bucket)),
            )
            val sanitized = AnalyticsSanitizer.sanitize(event, strict = true)
            assertEquals(AnalyticsParamValue.StringValue(bucket), sanitized.params["duration_bucket"])
        }
    }

    @Test
    fun `every declared size_bucket and retry_bucket value is accepted`() {
        listOf("lt_500kb", "500kb_1mb", "1_2mb", "gte_2mb").forEach { bucket ->
            val event = AnalyticsEvent(name = "x", params = mapOf("size_bucket" to AnalyticsParamValue.StringValue(bucket)))
            assertEquals(bucket, (AnalyticsSanitizer.sanitize(event, strict = true).params["size_bucket"] as AnalyticsParamValue.StringValue).value)
        }
        listOf("0", "1", "2", "gte_3").forEach { bucket ->
            val event = AnalyticsEvent(name = "x", params = mapOf("retry_bucket" to AnalyticsParamValue.StringValue(bucket)))
            assertEquals(bucket, (AnalyticsSanitizer.sanitize(event, strict = true).params["retry_bucket"] as AnalyticsParamValue.StringValue).value)
        }
    }

    @Test
    fun `a value just outside the bucket vocabulary throws in strict mode`() {
        val event = AnalyticsEvent(
            name = "post_upload_result",
            // "gte_30s" is valid; a made-up neighbour like "gte_60s" is not.
            params = mapOf("duration_bucket" to AnalyticsParamValue.StringValue("gte_60s")),
        )

        assertThrows(AnalyticsValidationException::class.java) {
            AnalyticsSanitizer.sanitize(event, strict = true)
        }
    }

    @Test
    fun `an out-of-vocabulary bucket value is dropped in lenient mode`() {
        val event = AnalyticsEvent(
            name = "post_upload_result",
            params = mapOf(
                "duration_bucket" to AnalyticsParamValue.StringValue("gte_60s"),
                "outcome" to AnalyticsParamValue.StringValue("success"),
            ),
        )

        val sanitized = AnalyticsSanitizer.sanitize(event, strict = false)

        assertNull(sanitized.params["duration_bucket"])
        assertEquals(AnalyticsParamValue.StringValue("success"), sanitized.params["outcome"])
    }

    @Test
    fun `a non-string value for a bucket key is rejected`() {
        val event = AnalyticsEvent(
            name = "post_upload_result",
            params = mapOf("retry_bucket" to AnalyticsParamValue.LongValue(3L)),
        )

        assertThrows(AnalyticsValidationException::class.java) {
            AnalyticsSanitizer.sanitize(event, strict = true)
        }
    }
}
