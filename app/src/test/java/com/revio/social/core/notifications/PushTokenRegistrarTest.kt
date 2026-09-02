package com.revio.social.core.notifications

import android.util.Log
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.data.local.auth.DeviceIdentity
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.remote.api.DeviceApi
import com.revio.social.data.remote.dto.device.DeviceDto
import com.revio.social.data.remote.dto.device.DevicePlatform
import com.revio.social.data.remote.dto.device.FirebaseProject
import com.revio.social.data.remote.dto.device.RegisterDeviceRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Covers [PushTokenRegistrar.registerCurrentToken]'s `send()` outcomes (plan §10 Pasul 5): a
 * network error queues the request for retry on reconnect, while a non-network error (e.g. a
 * 401 from an expired/missing session) is dropped without queuing — mirroring
 * [com.revio.social.data.repository.FeedbackRepositoryImplTest]'s offline-resilience coverage.
 */
class PushTokenRegistrarTest {

    @Before
    fun setUp() {
        // send() logs the outcome via android.util.Log, which isn't mocked by the plain JVM unit
        // test runtime — same as SettingsViewModelTest's mockkStatic for FirebaseCrashlytics.
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun registerRequest(deviceId: String = "device-1") = RegisterDeviceRequest(
        deviceId = deviceId,
        fcmToken = "token-1",
        firebaseProject = FirebaseProject.DEBUG,
        platform = DevicePlatform.ANDROID,
        appVersion = "1.0",
        timezone = "Europe/Bucharest",
        locale = "en-US",
    )

    private fun registrar(
        deviceApi: DeviceApi,
        pending: RegisterDeviceRequest? = null,
        internetValidated: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ): Pair<PushTokenRegistrar, UserPreferences> {
        var storedPending = pending
        val userPreferences: UserPreferences = mockk(relaxed = true)
        every { userPreferences.pendingDeviceRegistration } answers { flowOf(storedPending) }
        coEvery { userPreferences.setPendingDeviceRegistration(any()) } answers {
            storedPending = firstArg()
        }

        val deviceIdentity: DeviceIdentity = mockk(relaxed = true)
        every { deviceIdentity.id } returns "device-1"

        val connectivity: NetworkConnectivityManager = mockk(relaxed = true)
        every { connectivity.isInternetValidated } returns internetValidated

        val registrar = PushTokenRegistrar(
            deviceApi = deviceApi,
            deviceIdentity = deviceIdentity,
            userPreferences = userPreferences,
            networkConnectivityManager = connectivity,
            analyticsClient = null,
        )
        return registrar to userPreferences
    }

    private fun deviceDto(request: RegisterDeviceRequest) = DeviceDto(
        id = "server-id-1",
        deviceId = request.deviceId,
        firebaseProject = request.firebaseProject,
        platform = request.platform,
        appVersion = request.appVersion,
        timezone = request.timezone,
        locale = request.locale,
        isActive = true,
    )

    @Test
    fun `a successful registration clears any pending request`() = runTest {
        val request = registerRequest()
        val deviceApi: DeviceApi = mockk()
        coEvery { deviceApi.registerDevice(any()) } returns Response.success(deviceDto(request))
        val (registrar, userPreferences) = registrar(deviceApi, pending = request)

        // onNewToken() (unlike registerCurrentToken()) doesn't call FirebaseMessaging.getInstance()
        // first, so it's usable here without a real Firebase runtime.
        registrar.onNewToken("token-1")
        Thread.sleep(100)

        coVerify(exactly = 1) { userPreferences.setPendingDeviceRegistration(null) }
    }

    @Test
    fun `a network error queues the request for retry`() = runTest {
        val deviceApi: DeviceApi = mockk()
        coEvery { deviceApi.registerDevice(any()) } throws IOException()
        val (registrar, userPreferences) = registrar(deviceApi)

        registrar.onNewToken("token-1")
        Thread.sleep(100)

        coVerify(exactly = 1) { userPreferences.setPendingDeviceRegistration(any()) }
    }

    @Test
    fun `a 401 does not queue the request for retry`() = runTest {
        val deviceApi: DeviceApi = mockk()
        val errorBody = """{"error":"Unauthorized"}""".toResponseBody("application/json".toMediaType())
        coEvery { deviceApi.registerDevice(any()) } returns Response.error(401, errorBody)
        val (registrar, userPreferences) = registrar(deviceApi)

        registrar.onNewToken("token-1")
        Thread.sleep(100)

        coVerify(exactly = 0) { userPreferences.setPendingDeviceRegistration(any()) }
    }

    @Test
    fun `a validated reconnect retries a pending registration and clears it on success`() {
        val pending = registerRequest()
        val deviceApi: DeviceApi = mockk()
        coEvery { deviceApi.registerDevice(pending) } returns Response.success(deviceDto(pending))
        val internetValidated = MutableStateFlow(false)
        val (_, userPreferences) = registrar(deviceApi, pending = pending, internetValidated = internetValidated)

        Thread.sleep(100) // let the registrar's background collector subscribe before flipping
        internetValidated.value = true
        Thread.sleep(800) // past the 400ms reconnect debounce

        coVerify(exactly = 1) { deviceApi.registerDevice(pending) }
        coVerify(exactly = 1) { userPreferences.setPendingDeviceRegistration(null) }
    }
}
