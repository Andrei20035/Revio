package com.revio.social.data.local.db.feed

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached feed post. [createdAtIso] is [java.time.Instant.toString] verbatim so the keyset
 * cursor sent back to the server never loses sub-millisecond precision; [createdAtEpochMs]
 * only exists for ordering/eviction queries. [position] is the explicit ordering column —
 * it does not derive from [createdAtIso]/[id] so append/trim can't disturb existing rows.
 */
@Entity(
    tableName = "feed_posts",
    indices = [Index("position"), Index("createdAtEpochMs")],
)
data class FeedPostEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val authorProfilePictureUrl: String?,
    val brand: String,
    val model: String,
    val imageUrl: String,
    val caption: String?,
    val latitude: Double?,
    val longitude: Double?,
    val createdAtIso: String,
    val createdAtEpochMs: Long,
    val likeCount: Long,
    val commentCount: Long,
    val likedByCurrentUser: Boolean,
    val locationLabel: String?,
    val authorIsEarlySpotter: Boolean,
    val authorEarlySpotterNumber: Int?,
    val position: Int,
)
