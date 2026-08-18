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
    /**
     * Announcement keys (e.g. "EARLY_SPOTTER_WELCOME", "EARLY_SPOTTER_BONUS") the server created
     * PENDING in this exact call — mirrors [com.revio.server.features.user.dto.CreateUserResponse]
     * (server, `pendingAnnouncements`). Reserved: [isEarlySpotter]/[earlySpotterNumber]/
     * [earlySpotterBonusPoints] above remain the source of truth the client acts on (see
     * [com.revio.social.core.earlyspotter.EarlySpotterController.onProfileCreated]) — this field
     * exists so a client that later needs the exact announcement keys (rather than inferring them
     * from the other three) doesn't need a server contract change to get them. Not currently read.
     */
    val pendingAnnouncements: List<String> = emptyList(),
)
