package com.revio.app.core.network

import com.revio.app.core.auth.SessionManager
import com.revio.app.data.local.auth.AuthTokens
import com.revio.app.data.local.auth.DeviceIdentity
import com.revio.app.data.local.auth.TokenStore
import com.revio.app.data.remote.api.AuthApi
import com.revio.app.data.remote.dto.auth.AuthErrorCode
import com.revio.app.data.remote.dto.auth.AuthErrorResponse
import com.revio.app.data.remote.dto.auth.RefreshRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    @Named("refresh") private val refreshApi: AuthApi,
    private val deviceIdentity: DeviceIdentity,
    private val sessionManager: SessionManager,
    private val json: Json,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2 || isAuthEndpoint(response.request.url.encodedPath)) return null
        val errorCode = parseErrorCode(response) ?: return null
        if (errorCode != AuthErrorCode.ACCESS_TOKEN_EXPIRED) {
            if (errorCode == AuthErrorCode.SESSION_REVOKED ||
                errorCode == AuthErrorCode.SIGNED_IN_ON_ANOTHER_DEVICE
            ) runBlocking { sessionManager.expire(messageFor(errorCode)) }
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

            val refreshResponse = runBlocking {
                refreshApi.refresh(RefreshRequest(current.refreshToken, deviceIdentity.id))
            }
            if (!refreshResponse.isSuccessful) {
                val refreshCode = refreshResponse.errorBody()?.string()?.let(::parseErrorCode)
                if (refreshCode in terminalRefreshErrors) {
                    runBlocking { sessionManager.expire(messageFor(refreshCode)) }
                }
                return null
            }
            val body = refreshResponse.body() ?: return null
            tokenStore.save(AuthTokens(body.accessToken, body.refreshToken))
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${body.accessToken}")
                .build()
        }
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
        )
    }
}
