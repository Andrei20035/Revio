package com.revio.social.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResultTest {

    @Test
    fun `cause is null by default and readable once set`() {
        val error = ApiResult.Error("Unexpected error: boom")
        assertNull(error.cause)

        val thrown = RuntimeException("boom")
        error.cause = thrown

        assertEquals(thrown, error.cause)
    }

    @Test
    fun `requestId is null by default and readable once set`() {
        val error = ApiResult.Error("Server error")
        assertNull(error.requestId)

        error.requestId = "abc-123"

        assertEquals("abc-123", error.requestId)
    }

    @Test
    fun `equals ignores cause and requestId - same message and code are still equal`() {
        val a = ApiResult.Error("Server error", code = "http_5xx")
        val b = ApiResult.Error("Server error", code = "http_5xx")
        a.cause = RuntimeException("boom")
        b.cause = IllegalStateException("different exception entirely")
        a.requestId = "request-a"
        b.requestId = "request-b"

        assertTrue(a == b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `toString does not leak cause or requestId`() {
        val error = ApiResult.Error("Server error")
        error.cause = RuntimeException("do-not-leak-this-message")
        error.requestId = "do-not-leak-this-id"

        val text = error.toString()

        assertFalse(text.contains("do-not-leak-this-message"))
        assertFalse(text.contains("do-not-leak-this-id"))
    }
}
