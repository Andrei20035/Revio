package com.revio.app.data.remote.dto.post

import com.revio.app.core.network.serialization.InstantSerializer
import com.revio.app.core.network.serialization.UUIDSerializer
import com.revio.app.data.model.FeedPost
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Network shape of a feed item. Mirrors the server `PostDTO` returned by `GET /posts/feed`.
 * Kept separate from the generic [com.revio.app.data.model.Post] because the feed
 * response carries denormalized author/car fields and engagement counters.
 */
@Serializable
data class FeedPostDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val username: String,
    val authorProfilePictureUrl: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID? = null,
    val brand: String,
    val model: String,
    val imageUrl: String,
    val caption: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val town: String? = null,
    val country: String? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val likedByCurrentUser: Boolean = false,
    val authorIsEarlySpotter: Boolean = false,
    val authorEarlySpotterNumber: Int? = null,
)

fun FeedPostDto.toDomain(): FeedPost = FeedPost(
    id = id,
    userId = userId,
    username = username,
    authorProfilePictureUrl = authorProfilePictureUrl,
    brand = brand,
    model = model,
    imageUrl = imageUrl,
    caption = caption,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
    likeCount = likeCount,
    commentCount = commentCount,
    likedByCurrentUser = likedByCurrentUser,
    locationLabel = buildLocationLabel(town, country),
    authorIsEarlySpotter = authorIsEarlySpotter,
    authorEarlySpotterNumber = authorEarlySpotterNumber,
)

/** Joins town/country into the feed card's location label, tolerating either being absent. */
private fun buildLocationLabel(town: String?, country: String?): String? {
    val parts = listOfNotNull(town?.takeIf { it.isNotBlank() }, country?.takeIf { it.isNotBlank() })
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
