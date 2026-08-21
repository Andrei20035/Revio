package com.revio.social.core.network

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

/**
 * safeApiCall e singurul punct prin care toate erorile de network ajung la UI.
 * Dacă parsing-ul JSON crapă tăcut, userul vede mesaje aiurea în snackbar.
 * Aici acoperim toate ramurile reale (success / 4xx-5xx / IOException / non-JSON).
 */
class SafeApiCallTest {

    private val jsonMedia = "application/json".toMediaType()
    private val htmlMedia = "text/html".toMediaType()
    private val textMedia = "text/plain".toMediaType()

    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

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
    fun `error JSON cu obiect error nested extrage atat message cat si code`() = runTest {
        // Forma reala trimisa de server pentru erori auth: AuthErrorResponse { error: { code, message } }.
        val body = """{"error":{"code":"ACCOUNT_SUSPENDED","message":"Your account has been suspended."}}"""
            .toResponseBody(jsonMedia)

        val result = safeApiCall<String> { Response.error(403, body) }

        assertEquals("Your account has been suspended.", (result as ApiResult.Error).message)
        assertEquals("ACCOUNT_SUSPENDED", result.code)
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
    fun `UnknownHostException e marcata ca eroare de retea`() = runTest {
        val result = safeApiCall<String> { throw UnknownHostException("api.joinrevio.app") }

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).isNetworkError)
        assertEquals(ERROR_CODE_NETWORK, result.code)
    }

    @Test
    fun `NoConnectivityException e marcata ca eroare de retea`() = runTest {
        val result = safeApiCall<String> { throw NoConnectivityException() }

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).isNetworkError)
        assertEquals(ERROR_CODE_NETWORK, result.code)
    }

    @Test
    fun `eroare HTTP 500 nu e marcata ca eroare de retea`() = runTest {
        val body = "".toResponseBody(jsonMedia)

        val result = safeApiCall<String> { Response.error(500, body) }

        assertTrue(result is ApiResult.Error)
        assertFalse((result as ApiResult.Error).isNetworkError)
    }

    @Test
    fun `Exception generic devine Unexpected error`() = runTest {
        val result = safeApiCall<String> { throw RuntimeException("boom") }

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).message.startsWith("Unexpected error"))
    }

    @Test(expected = CancellationException::class)
    fun `CancellationException propaga in loc sa devina ApiResult Error`() = runTest {
        safeApiCall<String> { throw CancellationException("navigare/scope inchis") }
    }

    @Test(expected = CancellationException::class)
    fun `CancellationException propaga si in safeApiCallNoContent`() = runTest {
        safeApiCallNoContent { throw CancellationException("navigare/scope inchis") }
    }

    @Test
    fun `fara policy explicit, ApiResult Error e taguit REPORT`() = runTest {
        val result = safeApiCall<String> { throw RuntimeException("boom") }

        assertEquals(ErrorPolicy.REPORT, (result as ApiResult.Error).policy)
    }

    @Test
    fun `policy SILENT trece prin safeApiCall pana pe ApiResult Error`() = runTest {
        val result = safeApiCall<String>(policy = ErrorPolicy.SILENT) { throw RuntimeException("boom") }

        assertEquals(ErrorPolicy.SILENT, (result as ApiResult.Error).policy)
    }

    @Test
    fun `policy SILENT trece prin safeApiCallNoContent pana pe ApiResult Error`() = runTest {
        val result = safeApiCallNoContent(policy = ErrorPolicy.SILENT) { throw RuntimeException("boom") }

        assertEquals(ErrorPolicy.SILENT, (result as ApiResult.Error).policy)
    }

    @Test
    fun `IOException offline nu genereaza non-fatal`() = runTest {
        safeApiCall<String> { throw IOException("timeout") }

        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `eroare HTTP 5xx genereaza non-fatal cu stack`() = runTest {
        val body = """{"message":"db down"}""".toResponseBody(jsonMedia)

        safeApiCall<String> { Response.error(503, body) }

        verify(exactly = 1) { crashlytics.recordException(any()) }
    }

    @Test
    fun `eroare HTTP 4xx cunoscuta nu genereaza non-fatal`() = runTest {
        val body = """{"error":{"code":"EMAIL_TAKEN","message":"Email already in use"}}""".toResponseBody(jsonMedia)

        safeApiCall<String> { Response.error(409, body) }

        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `CancellationException cancel nu genereaza non-fatal`() = runTest {
        try {
            safeApiCall<String> { throw CancellationException("navigare/scope inchis") }
        } catch (_: CancellationException) {
            // propagarea e deja acoperita in alt test; aici verificam doar absenta raportarii
        }

        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `JsonDecodingException sintetic ajunge la Crashlytics cu stack`() = runTest {
        val decodingException = try {
            Json.decodeFromString<String>("{not valid json")
            error("ar fi trebuit sa arunce")
        } catch (e: Exception) {
            e
        }

        safeApiCall<String> { throw decodingException }

        verify(exactly = 1) { crashlytics.recordException(decodingException) }
    }

    @Test
    fun `policy SILENT nu raporteaza nici pentru o eroare 5xx`() = runTest {
        val body = "".toResponseBody(jsonMedia)

        safeApiCall<String>(policy = ErrorPolicy.SILENT) { Response.error(500, body) }

        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `policy BUSINESS nu raporteaza nici pentru o eroare 5xx`() = runTest {
        val body = "".toResponseBody(jsonMedia)

        safeApiCall<String>(policy = ErrorPolicy.BUSINESS) { Response.error(500, body) }

        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `policy BUSINESS nu raporteaza pentru o exceptie neasteptata`() = runTest {
        safeApiCall<String>(policy = ErrorPolicy.BUSINESS) { throw RuntimeException("boom") }

        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `policy BUSINESS trece prin safeApiCall pana pe ApiResult Error`() = runTest {
        val result = safeApiCall<String>(policy = ErrorPolicy.BUSINESS) { throw RuntimeException("boom") }

        assertEquals(ErrorPolicy.BUSINESS, (result as ApiResult.Error).policy)
    }
}
