package com.revio.app.data.model

import java.time.Instant
import java.util.UUID

/**
 * Domain model for a single comment shown in the comments overlay. Decoupled from the network
 * DTO and enriched with the author's display info ([username], [profilePictureUrl]).
 */
data class Comment(
    val id: UUID,
    val userId: UUID,
    val postId: UUID,
    val username: String,
    /** Author's profile picture (same field/format as the feed author avatar); null → placeholder. */
    val profilePictureUrl: String?,
    val text: String,
    val createdAt: Instant?,
)
