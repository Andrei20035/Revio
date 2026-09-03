package com.revio.social.di

import com.revio.social.core.network.NetworkConnectivityInterceptor
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pas 4 (docs/plans/avem-un-bug-android-mutable-sky.md) — the `/auth/refresh` client must never
 * mount [NetworkConnectivityInterceptor]: it's [com.revio.social.core.network.TokenAuthenticator]'s
 * only path back to a working session, so it must never be blocked by a connectivity check of
 * its own, cached or otherwise.
 */
class NetworkModuleRefreshClientTest {

    @Test
    fun `clientul de refresh nu monteaza NetworkConnectivityInterceptor`() {
        val requestIdInterceptor = Interceptor { chain -> chain.proceed(chain.request()) }
        val loggingInterceptor = HttpLoggingInterceptor()

        val client = NetworkModule.buildRefreshOkHttpClient(requestIdInterceptor, loggingInterceptor)

        assertFalse(client.interceptors.any { it is NetworkConnectivityInterceptor })
        assertTrue(client.interceptors.contains(requestIdInterceptor))
        assertTrue(client.interceptors.contains(loggingInterceptor))
        assertEquals(2, client.interceptors.size)
    }
}
