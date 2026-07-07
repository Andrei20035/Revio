package com.revio.app.data.remote.dto.auth

import com.revio.app.data.model.AuthProvider
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String? = null,
    val password: String? = null,
    val provider: AuthProvider,
    val googleIdToken: String? = null,
    val deviceId: String,
    val deviceName: String,
)
