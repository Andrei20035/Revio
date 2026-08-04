package com.revio.social.core.device

import android.os.Build
import com.revio.social.BuildConfig
import com.revio.social.core.network.NetworkConnectivityManager
import javax.inject.Inject
import javax.inject.Singleton

data class DiagnosticsInfo(
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String,
    val connectionType: String?,
)

@Singleton
class DeviceInfoProvider @Inject constructor(
    private val networkConnectivityManager: NetworkConnectivityManager,
) {
    fun collect(): DiagnosticsInfo = DiagnosticsInfo(
        appVersion = BuildConfig.VERSION_NAME,
        androidVersion = Build.VERSION.RELEASE.orEmpty(),
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        connectionType = networkConnectivityManager.connectionType.value,
    )
}
