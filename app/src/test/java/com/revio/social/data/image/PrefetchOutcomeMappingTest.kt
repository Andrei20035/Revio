package com.revio.social.data.image

import coil3.network.HttpException
import coil3.network.NetworkResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * [classifyPrefetchFailure] decides which images get retried and which are given up on for the
 * session (`FeedImageGate`'s `Skipped(permanent)` vs `Skipped(retryable)`). A wrong classification
 * here either wastes retries on a dead 404 or permanently drops an image that would have
 * succeeded on the next attempt.
 */
class PrefetchOutcomeMappingTest {

    private fun httpException(code: Int) = HttpException(NetworkResponse(code = code))

    @Test
    fun `404 este permanent`() {
        val outcome = classifyPrefetchFailure(httpException(404))
        assertTrue(outcome is PrefetchOutcome.PermanentFailure)
    }

    @Test
    fun `403 este permanent`() {
        val outcome = classifyPrefetchFailure(httpException(403))
        assertTrue(outcome is PrefetchOutcome.PermanentFailure)
    }

    @Test
    fun `429 este tranzitoriu`() {
        val outcome = classifyPrefetchFailure(httpException(429))
        assertTrue(outcome is PrefetchOutcome.TransientFailure)
    }

    @Test
    fun `500 este tranzitoriu`() {
        val outcome = classifyPrefetchFailure(httpException(500))
        assertTrue(outcome is PrefetchOutcome.TransientFailure)
    }

    @Test
    fun `503 este tranzitoriu`() {
        val outcome = classifyPrefetchFailure(httpException(503))
        assertTrue(outcome is PrefetchOutcome.TransientFailure)
    }

    @Test
    fun `alt cod 4xx (400) este permanent`() {
        val outcome = classifyPrefetchFailure(httpException(400))
        assertTrue(outcome is PrefetchOutcome.PermanentFailure)
    }

    @Test
    fun `UnknownHostException (DNS) este tranzitoriu`() {
        val outcome = classifyPrefetchFailure(UnknownHostException("no dns"))
        assertTrue(outcome is PrefetchOutcome.TransientFailure)
    }

    @Test
    fun `SocketTimeoutException este tranzitoriu`() {
        val outcome = classifyPrefetchFailure(SocketTimeoutException("timeout"))
        assertTrue(outcome is PrefetchOutcome.TransientFailure)
    }

    @Test
    fun `IOException generic este tranzitoriu`() {
        val outcome = classifyPrefetchFailure(IOException("connection reset"))
        assertTrue(outcome is PrefetchOutcome.TransientFailure)
    }

    @Test
    fun `eroare necunoscuta (ex decode failure) este permanenta`() {
        val outcome = classifyPrefetchFailure(IllegalStateException("corrupt image data"))
        assertTrue(outcome is PrefetchOutcome.PermanentFailure)
    }

    @Test
    fun `motivul permanent contine codul HTTP`() {
        val outcome = classifyPrefetchFailure(httpException(404)) as PrefetchOutcome.PermanentFailure
        assertEquals("HTTP 404", outcome.reason)
    }
}
