package com.revio.social.data.remote.dto.device

import kotlinx.serialization.Serializable

/** Mirrors the server's `FirebaseProject` enum (`revio-server`) — which Firebase project issued the FCM token. */
@Serializable
enum class FirebaseProject {
    DEBUG,
    RELEASE,
}

/** Mirrors the server's `DevicePlatform` enum (`revio-server`). Only ANDROID exists today. */
@Serializable
enum class DevicePlatform {
    ANDROID,
}

@Serializable
data class RegisterDeviceRequest(
    val deviceId: String,
    val fcmToken: String,
    val firebaseProject: FirebaseProject,
    val platform: DevicePlatform,
    val appVersion: String,
    val timezone: String? = null,
    val locale: String? = null,
)

@Serializable
data class DeviceDto(
    val id: String,
    val deviceId: String,
    val firebaseProject: FirebaseProject,
    val platform: DevicePlatform,
    val appVersion: String,
    val timezone: String?,
    val locale: String?,
    val isActive: Boolean,
)
