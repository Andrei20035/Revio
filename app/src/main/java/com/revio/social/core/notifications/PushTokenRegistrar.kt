package com.revio.social.core.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.revio.social.BuildConfig
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ErrorPolicy
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.core.network.isNetworkError
import com.revio.social.core.network.onValidatedReconnect
import com.revio.social.core.network.safeApiCall
import com.revio.social.core.network.safeApiCallNoContent
import com.revio.social.data.local.auth.DeviceIdentity
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.remote.api.DeviceApi
import com.revio.social.data.remote.dto.device.DevicePlatform
import com.revio.social.data.remote.dto.device.FirebaseProject
import com.revio.social.data.remote.dto.device.RegisterDeviceRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "PushTokenRegistrar"

/** Ev. push_token_register_result (§16, pas 7.2) — outcome of `POST /api/devices`. */
private const val EVENT_TOKEN_REGISTER_RESULT = "push_token_register_result"

/**
 * Uploads the device's FCM token to `POST /api/devices` (push-notifications plan, step 2.4).
 * Registration is an upsert on `(user_id, device_id)` server-side, so calling it again after
 * [onNewToken] (token rotation) or on every foreground (fresh timezone) updates the same row
 * rather than creating a new one.
 *
 * A failed upload while offline is queued in [UserPreferences.pendingDeviceRegistration] and
 * retried once connectivity is validated — same shape as [com.revio.social.data.repository.FeedbackRepositoryImpl].
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val deviceApi: DeviceApi,
    private val deviceIdentity: DeviceIdentity,
    private val userPreferences: UserPreferences,
    networkConnectivityManager: NetworkConnectivityManager,
    private val analyticsClient: AnalyticsClient? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            networkConnectivityManager.onValidatedReconnect().collect {
                retryPendingRegistration()
            }
        }
    }

    /** [com.revio.social.core.notifications.RevioMessagingService.onNewToken] — a rotated token. */
    fun onNewToken(token: String) {
        scope.launch { register(token) }
    }

    /**
     * Re-sends the current FCM token — called right after a successful login/register (once the
     * access token is saved) and on every app foreground, so timezone/locale/appVersion stay
     * current on the server row. If there's no session yet, the call simply 401s and is dropped
     * (SILENT policy, not a network error, so nothing is queued for retry).
     */
    fun registerCurrentToken() {
        scope.launch {
            val token = fetchToken() ?: return@launch
            register(token)
        }
    }

    /** `DELETE /api/devices/{deviceId}` — called on logout so the old token stops receiving push. */
    suspend fun unregisterCurrentDevice() {
        safeApiCallNoContent(policy = ErrorPolicy.SILENT) {
            deviceApi.deleteDevice(deviceIdentity.id)
        }
        userPreferences.setPendingDeviceRegistration(null)
    }

    /**
     * Drops any queued registration without contacting the server — called when the session
     * expires (refresh token itself failed), where an authenticated call isn't feasible. The
     * next successful login re-registers from scratch.
     */
    fun forgetPendingRegistration() {
        scope.launch { userPreferences.setPendingDeviceRegistration(null) }
    }

    private suspend fun register(token: String) {
        val request = RegisterDeviceRequest(
            deviceId = deviceIdentity.id,
            fcmToken = token,
            firebaseProject = if (BuildConfig.DEBUG) FirebaseProject.DEBUG else FirebaseProject.RELEASE,
            platform = DevicePlatform.ANDROID,
            appVersion = BuildConfig.VERSION_NAME,
            timezone = TimeZone.getDefault().id,
            locale = Locale.getDefault().toLanguageTag(),
        )
        send(request)
    }

    private suspend fun retryPendingRegistration() {
        val pending = userPreferences.pendingDeviceRegistration.first() ?: return
        send(pending)
    }

    private suspend fun send(request: RegisterDeviceRequest) {
        val result = safeApiCall(policy = ErrorPolicy.SILENT) { deviceApi.registerDevice(request) }
        logRegisterResult(result)
        when {
            result is ApiResult.Success -> userPreferences.setPendingDeviceRegistration(null)
            result is ApiResult.Error && result.isNetworkError -> userPreferences.setPendingDeviceRegistration(request)
            else -> Unit
        }
    }

    private fun logRegisterResult(result: ApiResult<*>) {
        val params = buildMap<String, AnalyticsParamValue> {
            put("outcome", AnalyticsParamValue.StringValue(if (result is ApiResult.Success) "success" else "failure"))
            if (result is ApiResult.Error) {
                put("failure_code", AnalyticsParamValue.StringValue(result.code ?: "unknown"))
            }
        }
        analyticsClient?.log(AnalyticsEvent(name = EVENT_TOKEN_REGISTER_RESULT, params = params))
    }

    private suspend fun fetchToken(): String? = try {
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> continuation.resume(token) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
    } catch (e: Exception) {
        Log.d(TAG, "Failed to fetch FCM token", e)
        null
    }
}
