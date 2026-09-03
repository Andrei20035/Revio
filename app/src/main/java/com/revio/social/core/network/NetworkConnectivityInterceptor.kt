package com.revio.social.core.network

import com.revio.social.core.analytics.CrashContext
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An OkHttp interceptor that checks for network connectivity before making API calls.
 * Throws a NoConnectivityException if there's no network connection.
 *
 * Trust-true, verify-false (pas 2, docs/plans/avem-un-bug-android-mutable-sky.md): a cached
 * `true` proceeds immediately, at zero extra cost. A cached `false` is never trusted on its own
 * — gating a request on a value that can go stale after a long background period (pas 0/1) would
 * be a false negative with no way to self-correct, since the only way the app can learn
 * connectivity has returned is to attempt a request, which this check would itself forbid. So a
 * cached `false` triggers one synchronous [NetworkConnectivityManager.refresh] and only rejects
 * the request if the freshly-checked state still says offline.
 */
@Singleton
class NetworkConnectivityInterceptor @Inject constructor(
    private val networkConnectivityManager: NetworkConnectivityManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!networkConnectivityManager.isNetworkAvailable.value) {
            networkConnectivityManager.refresh(source = "interceptor")
            if (!networkConnectivityManager.isNetworkAvailable.value) {
                // encodedPath only — never the full URL (no query string, no host-embedded
                // tokens). See docs/plans/avem-un-bug-android-mutable-sky.md, pas 0.
                CrashContext.breadcrumb(
                    "connectivity_interceptor_reject path=${chain.request().url.encodedPath} cachedAvailable=false"
                )
                throw NoConnectivityException()
            }
        }
        return chain.proceed(chain.request())
    }

}

/**
 * Exception thrown when there's no network connectivity.
 */
class NoConnectivityException : IOException("No network connection available")