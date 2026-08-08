package com.revio.social.core.network

import com.revio.social.core.auth.SessionManager
import com.revio.social.data.local.auth.AuthTokens
import com.revio.social.data.local.auth.DeviceIdentity
import com.revio.social.data.local.auth.TokenStore
import com.revio.social.data.remote.api.AuthApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
    private lateinit var authenticator: TokenAuthenticator

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val jsonMedia = "application/json".toMediaType()

    @Before
    fun setup() {
        tokenStore = mockk(relaxed = true)
        refreshApi = mockk()
        deviceIdentity = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        authenticator = TokenAuthenticator(tokenStore, refreshApi, deviceIdentity, sessionManager, json)
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
            sessionManager.expire("Your account has been suspended. Contact threvioapp@gmail.com if you believe this is a mistake.")
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
            sessionManager.expire("Your account has been suspended. Contact threvioapp@gmail.com if you believe this is a mistake.")
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

        coVerify(exactly = 0) { sessionManager.expire(any()) }
    }

    @Test
    fun `an expired access token that fails to refresh with a non-terminal code does not expire the session`() {
        every { tokenStore.read() } returns AuthTokens("expired-token", "refresh-token")
        every { deviceIdentity.id } returns "device-1"
        coEvery { refreshApi.refresh(any()) } returns RetrofitResponse.error(400, errorBody("VALIDATION_ERROR"))

        val original = responseWithCode("ACCESS_TOKEN_EXPIRED", httpCode = 401)

        val result = authenticator.authenticate(null, original)

        assertNull(result)
        coVerify(exactly = 0) { sessionManager.expire(any()) }
    }
}
