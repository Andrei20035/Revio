package com.revio.social.core.network

import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.analytics.CrashContext
import com.revio.social.core.auth.SessionManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.revio.social.data.local.auth.AuthTokens
import com.revio.social.data.local.auth.DeviceIdentity
import com.revio.social.data.local.auth.TokenStore
import com.revio.social.data.remote.api.AuthApi
import com.revio.social.data.remote.dto.auth.AuthErrorCode
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response as RetrofitResponse

/**
 * Pins Pas 6/Pas 8's ban enforcement on the client side: ACCOUNT_SUSPENDED must never be treated
 * as a refreshable ACCESS_TOKEN_EXPIRED, must end the session (like SESSION_REVOKED /
 * SIGNED_IN_ON_ANOTHER_DEVICE already do), and must surface its own message rather than the
 * generic "session expired" one — both when it's the original response's own code, and when it's
 * what /auth/refresh returns for a token that expired naturally after the account got banned.
 */
class TokenAuthenticatorTest {

    private lateinit var tokenStore: TokenStore
    private lateinit var refreshApi: AuthApi
    private lateinit var deviceIdentity: DeviceIdentity
    private lateinit var sessionManager: SessionManager
    private lateinit var analyticsClient: AnalyticsClient
    private lateinit var authenticator: TokenAuthenticator

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val jsonMedia = "application/json".toMediaType()

    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        CrashContext.resetBudgetForTests()

        tokenStore = mockk(relaxed = true)
        refreshApi = mockk()
        deviceIdentity = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        analyticsClient = mockk(relaxed = true)
        authenticator = TokenAuthenticator(tokenStore, refreshApi, deviceIdentity, sessionManager, json, analyticsClient)
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun errorBody(code: String, message: String = "err") =
        """{"error":{"code":"$code","message":"$message"}}""".toResponseBody(jsonMedia)

    private fun responseWithCode(code: String, httpCode: Int, path: String = "/api/posts/feed"): Response {
        val request = Request.Builder()
            .url("http://localhost$path")
            .header("Authorization", "Bearer expired-token")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(httpCode)
            .message("err")
            .body(errorBody(code))
            .build()
    }

    @Test
    fun `ACCOUNT_SUSPENDED as the original response's own code expires the session with the ban message, no retry`() {
        val response = responseWithCode("ACCOUNT_SUSPENDED", httpCode = 403)

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify(exactly = 1) {
            sessionManager.expire(
                "Your account has been suspended. Contact threvioapp@gmail.com if you believe this is a mistake.",
                "ACCOUNT_SUSPENDED",
            )
        }
    }

    @Test
    fun `an expired access token that fails to refresh with ACCOUNT_SUSPENDED expires the session with the ban message`() {
        every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
        every { deviceIdentity.id } returns "device-1"
        coEvery { refreshApi.refresh(any()) } returns
            RetrofitResponse.error(403, errorBody("ACCOUNT_SUSPENDED", "Your account has been suspended until 2026-08-20."))

        val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)

        val result = authenticator.authenticate(null, original)

        assertNull(result)
        coVerify(exactly = 1) {
            sessionManager.expire(
                "Your account has been suspended. Contact threvioapp@gmail.com if you believe this is a mistake.",
                "ACCOUNT_SUSPENDED",
            )
        }
        verify(exactly = 0) { tokenStore.save(any()) }
    }

    @Test
    fun `an expired access token that refreshes successfully does not expire the session`() {
        every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
        every { deviceIdentity.id } returns "device-1"
        coEvery { refreshApi.refresh(any()) } returns
            RetrofitResponse.success(
                com.revio.social.data.remote.dto.auth.AuthResponse(
                    accessToken = "new-token",
                    refreshToken = "new-refresh",
                    scope = "FULL",
                    onboardingStep = com.revio.social.data.remote.dto.auth.OnboardingStep.COMPLETED,
                )
            )

        val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)

        authenticator.authenticate(null, original)

        coVerify(exactly = 0) { sessionManager.expire(any(), any()) }
    }

    @Test
    fun `an expired access token that fails to refresh with a non-terminal code does not expire the session`() {
        every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
        every { deviceIdentity.id } returns "device-1"
        coEvery { refreshApi.refresh(any()) } returns RetrofitResponse.error(400, errorBody("VALIDATION_ERROR"))

        val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)

        val result = authenticator.authenticate(null, original)

        assertNull(result)
        coVerify(exactly = 0) { sessionManager.expire(any(), any()) }
    }

    // ----------------------------------------------------------------------
    // pas 2.4 — ev. 3 (token_refresh_result) + ev. 4 (session_expired), cele 7 coduri terminale
    // ----------------------------------------------------------------------

    @Test
    fun `cele 7 coduri terminale expira sesiunea cu failure_code din enum`() {
        val terminalCodes = listOf(
            AuthErrorCode.REFRESH_TOKEN_INVALID,
            AuthErrorCode.REFRESH_TOKEN_EXPIRED,
            AuthErrorCode.REFRESH_TOKEN_REUSED,
            AuthErrorCode.REFRESH_TOKEN_CONSUMED,
            AuthErrorCode.SESSION_REVOKED,
            AuthErrorCode.SIGNED_IN_ON_ANOTHER_DEVICE,
            AuthErrorCode.ACCOUNT_SUSPENDED,
        )

        for (code in terminalCodes) {
            every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
            every { deviceIdentity.id } returns "device-1"
            coEvery { refreshApi.refresh(any()) } returns RetrofitResponse.error(401, errorBody(code.name))

            val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)
            val result = authenticator.authenticate(null, original)

            assertNull(result)
            coVerify(exactly = 1) { sessionManager.expire(any(), code.name) }
        }
    }

    @Test
    fun `refresh reusit - token_refresh_result outcome success`() {
        every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
        every { deviceIdentity.id } returns "device-1"
        coEvery { refreshApi.refresh(any()) } returns
            RetrofitResponse.success(
                com.revio.social.data.remote.dto.auth.AuthResponse(
                    accessToken = "new-token",
                    refreshToken = "new-refresh",
                    scope = "FULL",
                    onboardingStep = com.revio.social.data.remote.dto.auth.OnboardingStep.COMPLETED,
                )
            )

        val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)
        authenticator.authenticate(null, original)

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "token_refresh_result",
                    params = mapOf("outcome" to AnalyticsParamValue.StringValue("success")),
                )
            )
        }
    }

    @Test
    fun `refresh esuat cu cod terminal - token_refresh_result outcome failure cu failure_code din enum`() {
        every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
        every { deviceIdentity.id } returns "device-1"
        coEvery { refreshApi.refresh(any()) } returns RetrofitResponse.error(401, errorBody("REFRESH_TOKEN_EXPIRED"))

        val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)
        authenticator.authenticate(null, original)

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "token_refresh_result",
                    params = mapOf(
                        "outcome" to AnalyticsParamValue.StringValue("failure"),
                        "failure_code" to AnalyticsParamValue.StringValue("REFRESH_TOKEN_EXPIRED"),
                    ),
                )
            )
        }
    }

    @Test
    fun `refresh esuat cu cod necunoscut - token_refresh_result failure_code unrecognized`() {
        every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
        every { deviceIdentity.id } returns "device-1"
        coEvery { refreshApi.refresh(any()) } returns
            RetrofitResponse.error(500, "".toResponseBody(jsonMedia))

        val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)
        authenticator.authenticate(null, original)

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "token_refresh_result",
                    params = mapOf(
                        "outcome" to AnalyticsParamValue.StringValue("failure"),
                        "failure_code" to AnalyticsParamValue.StringValue("unrecognized"),
                    ),
                )
            )
        }
    }

    @Test
    fun `raspuns de refresh gol - token_refresh_result failure_code empty_response`() {
        every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
        every { deviceIdentity.id } returns "device-1"
        coEvery { refreshApi.refresh(any()) } returns RetrofitResponse.success(null)

        val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)
        authenticator.authenticate(null, original)

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "token_refresh_result",
                    params = mapOf(
                        "outcome" to AnalyticsParamValue.StringValue("failure"),
                        "failure_code" to AnalyticsParamValue.StringValue("empty_response"),
                    ),
                )
            )
        }
    }

    @Test
    fun `cod terminal direct pe raspunsul original - session_expired dar fara token_refresh_result`() {
        val response = responseWithCode("SESSION_REVOKED", httpCode = 401)

        authenticator.authenticate(null, response)

        coVerify(exactly = 1) { sessionManager.expire(any(), "SESSION_REVOKED") }
        verify(exactly = 0) { analyticsClient.log(match { it.name == "token_refresh_result" }) }
    }

    // ----------------------------------------------------------------------
    // pas 0 (docs/plans/avem-un-bug-android-mutable-sky.md) — the token_refresh_result breadcrumb
    // must never leak the access/refresh token values themselves.
    // ----------------------------------------------------------------------

    @Test
    fun `breadcrumb-ul de refresh reusit nu contine tokenul sau prefixul Bearer`() {
        every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
        every { deviceIdentity.id } returns "device-1"
        coEvery { refreshApi.refresh(any()) } returns
            RetrofitResponse.success(
                com.revio.social.data.remote.dto.auth.AuthResponse(
                    accessToken = "new-token",
                    refreshToken = "new-refresh",
                    scope = "FULL",
                    onboardingStep = com.revio.social.data.remote.dto.auth.OnboardingStep.COMPLETED,
                )
            )

        val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)
        authenticator.authenticate(null, original)

        val logged: CapturingSlot<String> = slot()
        verify { crashlytics.log(capture(logged)) }
        assertFalse(logged.captured.contains("Bearer", ignoreCase = true))
        assertFalse(logged.captured.contains("new-token"))
        assertFalse(logged.captured.contains("new-refresh"))
        assertFalse(logged.captured.contains("expired-token"))
        assertFalse(logged.captured.contains("refresh-token"))
        assertFalse(logged.captured.contains("?"))
    }

    @Test
    fun `breadcrumb-ul de refresh esuat cu cod terminal nu contine tokenul`() {
        every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
        every { deviceIdentity.id } returns "device-1"
        coEvery { refreshApi.refresh(any()) } returns RetrofitResponse.error(401, errorBody("REFRESH_TOKEN_EXPIRED"))

        val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)
        authenticator.authenticate(null, original)

        val logged: CapturingSlot<String> = slot()
        verify { crashlytics.log(capture(logged)) }
        assertFalse(logged.captured.contains("Bearer", ignoreCase = true))
        assertFalse(logged.captured.contains("expired-token"))
        assertFalse(logged.captured.contains("refresh-token"))
        assertFalse(logged.captured.contains("?"))
    }

    // ----------------------------------------------------------------------
    // pas 4 (docs/plans/avem-un-bug-android-mutable-sky.md) — a network failure during
    // /auth/refresh must stay non-terminal: refreshApi is called regardless of connectivity
    // (TokenAuthenticator never checks it directly — isolation is enforced one layer down, in
    // NetworkModuleRefreshClientTest), and an IOException from it must never expire the session.
    // ----------------------------------------------------------------------

    @Test
    fun `un IOException la refresh nu expira sesiunea si se propaga`() {
        every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
        every { deviceIdentity.id } returns "device-1"
        coEvery { refreshApi.refresh(any()) } throws NoConnectivityException()

        val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)

        org.junit.Assert.assertThrows(NoConnectivityException::class.java) {
            authenticator.authenticate(null, original)
        }
        coVerify(exactly = 0) { sessionManager.expire(any(), any()) }
        verify(exactly = 0) { analyticsClient.log(match { it.name == "token_refresh_result" }) }
    }
}
