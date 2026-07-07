package com.revio.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An OkHttp interceptor that checks for network connectivity before making API calls.
 * Throws a NoConnectivityException if there's no network connection.
 */
@Singleton
class NetworkConnectivityInterceptor @Inject constructor(
    private val networkConnectivityManager: NetworkConnectivityManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!networkConnectivityManager.isNetworkAvailable.value) {
            throw NoConnectivityException()
        }
        return chain.proceed(chain.request())
    }

}

/**
 * Exception thrown when there's no network connectivity.
 */
class NoConnectivityException : IOException("No network connection available")