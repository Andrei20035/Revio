package com.revio.social.di

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * pas 1.8 — X-Request-Id trebuie să corelize fiecare request cu logul de server (pas 3.1).
 * Doar header-ul contează aici; citirea răspunsului rămâne în afara scopului.
 */
class NetworkModuleRequestIdInterceptorTest {

    private fun fakeChain(): Pair<Interceptor.Chain, () -> Request> {
        val originalRequest = Request.Builder().url("https://api.joinrevio.app/api/feed").build()
        val chain = mockk<Interceptor.Chain>()
        val requestSlot = slot<Request>()
        every { chain.request() } returns originalRequest
        every { chain.proceed(capture(requestSlot)) } answers {
            Response.Builder()
                .request(requestSlot.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }
        return chain to { requestSlot.captured }
    }

    @Test
    fun `adauga header X-Request-Id pe fiecare request`() {
        val interceptor = NetworkModule.provideRequestIdInterceptor()
        val (chain, capturedRequest) = fakeChain()

        interceptor.intercept(chain)

        assertNotNull(capturedRequest().header("X-Request-Id"))
    }

    @Test
    fun `fiecare request primeste un UUID unic`() {
        val interceptor = NetworkModule.provideRequestIdInterceptor()
        val (chain1, captured1) = fakeChain()
        val (chain2, captured2) = fakeChain()

        interceptor.intercept(chain1)
        interceptor.intercept(chain2)

        assertNotEquals(captured1().header("X-Request-Id"), captured2().header("X-Request-Id"))
    }
}
