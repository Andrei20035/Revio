package com.revio.social.core.network

import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.analytics.CrashContext
import com.revio.social.core.auth.SessionManager
import com.revio.social.data.local.auth.AuthTokens
import com.revio.social.data.local.auth.DeviceIdentity
import com.revio.social.data.local.auth.TokenStore
import com.revio.social.data.remote.api.AuthApi
import com.revio.social.data.remote.dto.auth.AuthErrorCode
import com.revio.social.data.remote.dto.auth.AuthErrorResponse
import com.revio.social.data.remote.dto.auth.RefreshRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.IOException
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named

/** ev. 3 — fired for every /auth/refresh attempt this authenticator makes. */
private const val EVENT_TOKEN_REFRESH_RESULT = "token_refresh_result"

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    @Named("refresh") private val refreshApi: AuthApi,
    private val deviceIdentity: DeviceIdentity,
    private val sessionManager: SessionManager,
    private val json: Json,
    private val analyticsClient: AnalyticsClient? = null,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2 || isAuthEndpoint(response.request.url.encodedPath)) return null
        val errorCode = parseErrorCode(response) ?: return null
        if (errorCode != AuthErrorCode.ACCESS_TOKEN_EXPIRED) {
            if (errorCode == AuthErrorCode.SESSION_REVOKED ||
                errorCode == AuthErrorCode.SIGNED_IN_ON_ANOTHER_DEVICE ||
                errorCode == AuthErrorCode.ACCOUNT_SUSPENDED
            ) runBlocking { sessionManager.expire(message = messageFor(errorCode), failureCode = errorCode.name) }
            return null
        }

        synchronized(lock) {
            val current = tokenStore.read() ?: return null
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (requestToken != current.accessToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${current.accessToken}")
                    .build()
            }

            val refreshResponse = try {
                runBlocking {
                    refreshApi.refresh(RefreshRequest(current.refreshToken, deviceIdentity.id))
                }
            } catch (e: IOException) {
                // Observability only — behavior unchanged: this still propagates and must never
                // trigger sessionManager.expire() (a network failure is not a terminal auth
                // error). See docs/plans/avem-un-bug-android-mutable-sky.md, pas 0.
                CrashContext.breadcrumb(
                    "connectivity_token_refresh_io_exception type=${e::class.simpleName}"
                )
                throw e
            }
            if (!refreshResponse.isSuccessful) {
                val refreshCode = refreshResponse.errorBody()?.string()?.let(::parseErrorCode)
                logTokenRefreshResult(success = false, failureCode = refreshCode?.name ?: "unrecognized")
                if (refreshCode != null && refreshCode in terminalRefreshErrors) {
                    runBlocking { sessionManager.expire(message = messageFor(refreshCode), failureCode = refreshCode.name) }
                }
                return null
            }
            val body = refreshResponse.body() ?: run {
                logTokenRefreshResult(success = false, failureCode = "empty_response")
                return null
            }
            tokenStore.save(AuthTokens(body.accessToken, body.refreshToken))
            logTokenRefreshResult(success = true)
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${body.accessToken}")
                .build()
        }
    }

    private fun logTokenRefreshResult(success: Boolean, failureCode: String? = null) {
        val params = buildMap<String, AnalyticsParamValue> {
            put("outcome", AnalyticsParamValue.StringValue(if (success) "success" else "failure"))
            failureCode?.let { put("failure_code", AnalyticsParamValue.StringValue(it)) }
        }
        analyticsClient?.log(AnalyticsEvent(name = EVENT_TOKEN_REFRESH_RESULT, params = params))
        // No tokens — outcome and failure code only. See docs/plans/avem-un-bug-android-mutable-sky.md, pas 0.
        CrashContext.breadcrumb(
            "connectivity_token_refresh_result outcome=${if (success) "success" else "failure"}" +
                (failureCode?.let { " failure_code=$it" } ?: "")
        )
    }

    private fun parseErrorCode(response: Response): AuthErrorCode? =
        response.peekBody(16_384).string().let(::parseErrorCode)

    private fun parseErrorCode(body: String): AuthErrorCode? = runCatching {
        json.decodeFromString<AuthErrorResponse>(body).error.code
    }.getOrNull()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun isAuthEndpoint(path: String) =
        path.endsWith("/auth/login") || path.endsWith("/auth/register") ||
            path.endsWith("/auth/refresh")

    private fun messageFor(code: AuthErrorCode?) = when (code) {
        AuthErrorCode.SIGNED_IN_ON_ANOTHER_DEVICE ->
            "You signed in on another device. Please sign in again."
        AuthErrorCode.ACCOUNT_SUSPENDED ->
            "Your account has been suspended. Contact threvioapp@gmail.com if you believe this is a mistake."
        else -> "Your session has expired. Please sign in again."
    }

    private companion object {
        val terminalRefreshErrors = setOf(
            AuthErrorCode.REFRESH_TOKEN_INVALID,
            AuthErrorCode.REFRESH_TOKEN_EXPIRED,
            AuthErrorCode.REFRESH_TOKEN_REUSED,
            AuthErrorCode.REFRESH_TOKEN_CONSUMED,
            AuthErrorCode.SESSION_REVOKED,
            AuthErrorCode.SIGNED_IN_ON_ANOTHER_DEVICE,
            AuthErrorCode.ACCOUNT_SUSPENDED,
        )
    }
}
