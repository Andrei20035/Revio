package com.revio.app.data.remote.dto.auth

import kotlinx.serialization.Serializable

/**
 * Data class representing a password update request to the API.
 */
@Serializable
data class UpdatePasswordRequest(
    val oldPassword: String,
    val newPassword: String,
)