package com.revio.app.data.remote.dto.auth

import kotlinx.serialization.Serializable

@Serializable
data class RefreshRequest(
    val refreshToken: String,
    val deviceId: String,
)
