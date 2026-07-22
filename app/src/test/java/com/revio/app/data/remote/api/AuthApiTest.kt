package com.revio.app.data.remote.api

import com.revio.app.data.remote.dto.auth.DeleteAccountRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Regression test for the Retrofit proxy-construction bug where `@DELETE` combined with
 * `@Body` throws `IllegalArgumentException: Non-body HTTP method cannot contain @Body`
 * the first time `deleteAccount` is invoked. Mock-based tests (e.g. AuthRepositoryImplTest)
 * mock AuthApi directly and never build the real Retrofit proxy, so they cannot catch this.
 */
class AuthApiTest {

    private class CapturingCallFactory : Call.Factory {
        var capturedRequest: Request? = null

        override fun newCall(request: Request): Call {
            capturedRequest = request
            return FakeCall(request)
        }

        private inner class FakeCall(private val request: Request) : Call {
            override fun execute(): Response = Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()

            override fun enqueue(responseCallback: okhttp3.Callback) {
                responseCallback.onResponse(this, execute())
            }

            override fun cancel() = Unit
            override fun isExecuted(): Boolean = false
            override fun isCanceled(): Boolean = false
            override fun timeout(): okio.Timeout = okio.Timeout.NONE
            override fun clone(): Call = FakeCall(request)
            override fun request(): Request = request
            override fun <T> tag(type: Class<out T>): T? = null
            override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
            override fun <T : Any> tag(type: kotlin.reflect.KClass<T>): T? = null
            override fun <T : Any> tag(type: kotlin.reflect.KClass<T>, computeIfAbsent: () -> T): T =
                computeIfAbsent()
        }
    }

    @Test
    fun `deleteAccount builds a DELETE request that carries a body`() = runTest {
        val callFactory = CapturingCallFactory()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val contentType = "application/json".toMediaType()

        val authApi = Retrofit.Builder()
            .baseUrl("http://localhost/api/")
            .callFactory(callFactory)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AuthApi::class.java)

        authApi.deleteAccount(DeleteAccountRequest(password = "Passw0rd!"))

        val request = requireNotNull(callFactory.capturedRequest)
        assertEquals("DELETE", request.method)
        val body = request.body
        assertTrue("expected @Body to be attached to the DELETE request", body != null)
        val buffer = Buffer()
        body!!.writeTo(buffer)
        assertTrue(buffer.size > 0)
    }
}
