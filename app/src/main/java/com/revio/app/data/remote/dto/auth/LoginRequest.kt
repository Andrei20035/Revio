package com.revio.app.data.remote.dto.auth

import com.revio.app.data.model.AuthProvider
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String? = null,
    val password: String? = null,
    val googleIdToken: String? = null,
    val provider: AuthProvider,
    val deviceId: String,
    val deviceName: String,
)
