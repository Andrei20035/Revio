package com.revio.app.data.local.db.feed

import com.revio.app.data.model.FeedPost
import java.time.Instant
import java.util.UUID

fun FeedPostEntity.toDomain(): FeedPost = FeedPost(
    id = UUID.fromString(id),
    userId = UUID.fromString(userId),
    username = username,
    authorProfilePictureUrl = authorProfilePictureUrl,
    brand = brand,
    model = model,
    imageUrl = imageUrl,
    caption = caption,
    latitude = latitude,
    longitude = longitude,
    createdAt = Instant.parse(createdAtIso),
    likeCount = likeCount,
    commentCount = commentCount,
    likedByCurrentUser = likedByCurrentUser,
    locationLabel = locationLabel,
    authorIsEarlySpotter = authorIsEarlySpotter,
    authorEarlySpotterNumber = authorEarlySpotterNumber,
)

fun FeedPost.toEntity(position: Int): FeedPostEntity = FeedPostEntity(
    id = id.toString(),
    userId = userId.toString(),
    username = username,
    authorProfilePictureUrl = authorProfilePictureUrl,
    brand = brand,
    model = model,
    imageUrl = imageUrl,
    caption = caption,
    latitude = latitude,
    longitude = longitude,
    createdAtIso = createdAt.toString(),
    createdAtEpochMs = createdAt.toEpochMilli(),
    likeCount = likeCount,
    commentCount = commentCount,
    likedByCurrentUser = likedByCurrentUser,
    locationLabel = locationLabel,
    authorIsEarlySpotter = authorIsEarlySpotter,
    authorEarlySpotterNumber = authorEarlySpotterNumber,
    position = position,
)
