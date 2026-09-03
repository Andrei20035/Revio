package com.revio.social.core.network

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * pas 2 (docs/plans/avem-un-bug-android-mutable-sky.md) — trust-true, verify-false. A cached
 * `true` must proceed without touching [NetworkConnectivityManager.refresh]. A cached `false`
 * must not be trusted on its own: it re-checks once via `refresh()` and only rejects the request
 * if the fresh state still says offline — this is what breaks the auto-perpetuating block a
 * stale cached value used to cause.
 */
class NetworkConnectivityInterceptorTest {

    private lateinit var networkConnectivityManager: NetworkConnectivityManager
    private lateinit var interceptor: NetworkConnectivityInterceptor
    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        networkConnectivityManager = mockk(relaxed = true)
        interceptor = NetworkConnectivityInterceptor(networkConnectivityManager)
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun fakeChain(): Interceptor.Chain {
        val request = Request.Builder().url("https://api.joinrevio.app/api/feed").build()
        return mockk {
            every { request() } returns request
            every { proceed(any()) } returns Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }
    }

    @Test
    fun `cached true - proceed direct, fara refresh`() {
        every { networkConnectivityManager.isNetworkAvailable.value } returns true
        val chain = fakeChain()

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        verify(exactly = 0) { networkConnectivityManager.refresh(any()) }
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test
    fun `cached false, sistem online - refresh apelat, request executat fara exceptie`() {
        var refreshed = false
        every { networkConnectivityManager.isNetworkAvailable.value } answers { refreshed }
        every { networkConnectivityManager.refresh(any()) } answers { refreshed = true }
        val chain = fakeChain()

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        verify(exactly = 1) { networkConnectivityManager.refresh("interceptor") }
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test
    fun `cached false, sistem offline - NoConnectivityException`() {
        every { networkConnectivityManager.isNetworkAvailable.value } returns false
        val chain = fakeChain()

        assertThrows(NoConnectivityException::class.java) {
            interceptor.intercept(chain)
        }
        verify(exactly = 1) { networkConnectivityManager.refresh("interceptor") }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `refresh este apelat cel mult o data per request`() {
        every { networkConnectivityManager.isNetworkAvailable.value } returns false
        val chain = fakeChain()

        assertThrows(NoConnectivityException::class.java) {
            interceptor.intercept(chain)
        }
        verify(exactly = 1) { networkConnectivityManager.refresh(any()) }
    }
}
