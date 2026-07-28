package com.revio.social.data.remote.dto.user

import kotlinx.serialization.Serializable

@Serializable
data class UsernameAvailabilityResponse(
    val available: Boolean,
    val normalized: String,
    val reason: String? = null,
)
