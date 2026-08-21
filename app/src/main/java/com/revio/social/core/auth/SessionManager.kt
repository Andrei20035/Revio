package com.revio.social.core.auth

import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.data.local.auth.TokenStore
import com.revio.social.data.local.cache.FeedCache
import com.revio.social.data.local.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ev. 4 — fired on every forced session expiration. [expire]'s [failureCode] parameter is
 * mandatory (not defaulted) so this can never fire without one — see pas 2.4.
 */
private const val EVENT_SESSION_EXPIRED = "session_expired"

@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val userPreferences: UserPreferences,
    private val feedCache: FeedCache,
    private val analyticsClient: AnalyticsClient? = null,
) {
    private val _expired = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val expired = _expired.asSharedFlow()

    /** [failureCode] is one of [com.revio.social.data.remote.dto.auth.AuthErrorCode]'s 7 terminal codes — see [com.revio.social.core.network.TokenAuthenticator]. */
    suspend fun expire(
        message: String = "Your session has expired. Please sign in again.",
        failureCode: String,
    ) {
        tokenStore.clear()
        userPreferences.clearAuthData()
        feedCache.clear()
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_SESSION_EXPIRED,
                params = mapOf("failure_code" to AnalyticsParamValue.StringValue(failureCode)),
            )
        )
        _expired.emit(message)
    }
}
