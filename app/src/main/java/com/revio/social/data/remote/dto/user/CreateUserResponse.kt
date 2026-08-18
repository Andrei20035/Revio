package com.revio.social.data.remote.dto.user

import com.revio.social.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/** Mirrors the server's CreateUserResponse (see UserRoutes.kt POST /users). */
@Serializable
data class CreateUserResponse(
    val accessToken: String,
    val refreshToken: String,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val isEarlySpotter: Boolean = false,
    val earlySpotterNumber: Int? = null,
    /** Non-null only when the 300-point Early Spotter bonus was granted by this exact call. */
    val earlySpotterBonusPoints: Int? = null,
    /** Announcement keys (e.g. "EARLY_SPOTTER_WELCOME") the client can show immediately. */
    val pendingAnnouncements: List<String> = emptyList(),
)
