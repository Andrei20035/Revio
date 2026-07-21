package com.revio.app.data.remote.dto.auth

import kotlinx.serialization.Serializable

@Serializable
data class DeleteAccountRequest(
    val password: String? = null,
    val usernameConfirmation: String? = null,
    val reason: String? = null,
    val details: String? = null,
)
