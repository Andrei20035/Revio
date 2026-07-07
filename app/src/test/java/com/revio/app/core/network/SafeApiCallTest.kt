package com.revio.app.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * safeApiCall e singurul punct prin care toate erorile de network ajung la UI.
 * Dacă parsing-ul JSON crapă tăcut, userul vede mesaje aiurea în snackbar.
 * Aici acoperim toate ramurile reale (success / 4xx-5xx / IOException / non-JSON).
 */
class SafeApiCallTest {

    private val jsonMedia = "application/json".toMediaType()
    private val htmlMedia = "text/html".toMediaType()
    private val textMedia = "text/plain".toMediaType()

    @Test
    fun `success cu body returneaza ApiResult Success`() = runTest {
        val result = safeApiCall { Response.success("payload") }

        assertTrue(result is ApiResult.Success)
        assertEquals("payload", (result as ApiResult.Success).data)
    }

    @Test
    fun `success cu body null returneaza Empty response`() = runTest {
        val response = Response.success<Any?>(null)

        val result = safeApiCall { response }

        assertTrue(result is ApiResult.Error)
        assertEquals("Empty response", (result as ApiResult.Error).message)
    }

    @Test
    fun `error JSON cu campul error e parsat corect`() = runTest {
        val body = """{"error":"Invalid credentials"}""".toResponseBody(jsonMedia)

        val result = safeApiCall<String> { Response.error(401, body) }

        assertEquals("Invalid credentials", (result as ApiResult.Error).message)
    }

    @Test
    fun `error JSON fara error dar cu message foloseste message`() = runTest {
        val body = """{"message":"Email already in use"}""".toResponseBody(jsonMedia)

        val result = safeApiCall<String> { Response.error(409, body) }

        assertEquals("Email already in use", (result as ApiResult.Error).message)
    }

    @Test
    fun `error JSON fara error si fara message returneaza Unknown error`() = runTest {
        val body = """{"foo":"bar"}""".toResponseBody(jsonMedia)

        val result = safeApiCall<String> { Response.error(400, body) }

        assertEquals("Unknown error", (result as ApiResult.Error).message)
    }

    @Test
    fun `error body gol returneaza Unknown error`() = runTest {
        val body = "".toResponseBody(jsonMedia)

        val result = safeApiCall<String> { Response.error(500, body) }

        assertEquals("Unknown error", (result as ApiResult.Error).message)
    }

    @Test
    fun `error body HTML nu se afiseaza userului - fallback la Server error`() = runTest {
        val body = "<html><body>500 Internal Server Error</body></html>".toResponseBody(htmlMedia)

        val result = safeApiCall<String> { Response.error(500, body) }

        assertEquals("Server error", (result as ApiResult.Error).message)
    }

    @Test
    fun `error body text scurt non-JSON e returnat ca atare`() = runTest {
        val body = "rate limit exceeded".toResponseBody(textMedia)

        val result = safeApiCall<String> { Response.error(429, body) }

        assertEquals("rate limit exceeded", (result as ApiResult.Error).message)
    }

    @Test
    fun `error body text lung non-JSON e ascuns ca Server error`() = runTest {
        val long = "x".repeat(250)
        val body = long.toResponseBody(textMedia)

        val result = safeApiCall<String> { Response.error(500, body) }

        assertEquals("Server error", (result as ApiResult.Error).message)
    }

    @Test
    fun `IOException devine Network error`() = runTest {
        val result = safeApiCall<String> { throw IOException("timeout") }

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).message.startsWith("Network error"))
    }

    @Test
    fun `Exception generic devine Unexpected error`() = runTest {
        val result = safeApiCall<String> { throw RuntimeException("boom") }

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).message.startsWith("Unexpected error"))
    }
}
