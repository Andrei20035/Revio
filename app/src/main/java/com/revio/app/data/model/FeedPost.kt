package com.revio.app.data.model

import java.time.Instant
import java.util.UUID

/**
 * Domain model for a single feed item. Decoupled from the network DTO so the UI layer
 * does not depend on serialization concerns.
 */
data class FeedPost(
    val id: UUID,
    val userId: UUID,
    val username: String,
    /** Author's profile picture (full URL), or null to fall back to the placeholder avatar. */
    val authorProfilePictureUrl: String? = null,
    val brand: String,
    val model: String,
    val imageUrl: String,
    val caption: String?,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: Instant,
    val likeCount: Long,
    val commentCount: Long,
    val likedByCurrentUser: Boolean,
    /**
     * Human-readable place name shown next to the car (e.g. "Bucharest, Romania" in the design).
     * The current feed DTO only exposes [latitude]/[longitude], so this is null until the backend
     * provides a resolved place name — the UI hides the location row rather than fabricate one.
     */
    val locationLabel: String? = null,
    val authorIsEarlySpotter: Boolean = false,
    val authorEarlySpotterNumber: Int? = null,
) {
    /** e.g. "Porsche 911" — used as the headline car label in the feed card. */
    val carName: String get() = "$brand $model"

    val authorShowEarlySpotter: Boolean get() = authorIsEarlySpotter && authorEarlySpotterNumber != null
}
