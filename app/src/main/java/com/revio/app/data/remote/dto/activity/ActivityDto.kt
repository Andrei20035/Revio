package com.revio.app.data.remote.dto.activity

import com.revio.app.core.network.serialization.InstantSerializer
import com.revio.app.core.network.serialization.UUIDSerializer
import com.revio.app.features.activity.model.ActivityData
import com.revio.app.features.activity.model.ActivityItem
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class ActivityItemDto(
    val type: String,
    val id: String,
    @Serializable(with = UUIDSerializer::class)
    val actorUserId: UUID? = null,
    val actorUsername: String? = null,
    val actorAvatarUrl: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val postId: UUID? = null,
    val postThumbnailUrl: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val commentText: String? = null,
    val placesMoved: Int? = null,
    val streakDays: Int? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
)

@Serializable
data class ActivityResponseDto(
    val weeklySpotScore: Int,
    val todayInteractions: Int,
    val items: List<ActivityItemDto>,
)

fun ActivityItemDto.toDomain(): ActivityItem? = when (type) {
    "LIKE" -> {
        val actor = actorUserId
        val post = postId
        if (actor != null && post != null) {
            ActivityItem.LikeItem(
                id = id,
                createdAt = createdAt,
                actorUserId = actor,
                actorUsername = actorUsername.orEmpty(),
                actorAvatarUrl = actorAvatarUrl,
                postId = post,
                postThumbnailUrl = postThumbnailUrl,
                brand = brand,
                model = model,
            )
        } else null
    }

    "COMMENT" -> {
        val actor = actorUserId
        val post = postId
        if (actor != null && post != null) {
            ActivityItem.CommentItem(
                id = id,
                createdAt = createdAt,
                actorUserId = actor,
                actorUsername = actorUsername.orEmpty(),
                actorAvatarUrl = actorAvatarUrl,
                postId = post,
                postThumbnailUrl = postThumbnailUrl,
                brand = brand,
                model = model,
                commentText = commentText.orEmpty(),
            )
        } else null
    }

    "LEADERBOARD_UP" -> ActivityItem.LeaderboardUpItem(
        id = id,
        createdAt = createdAt,
        placesMoved = placesMoved ?: 0,
    )

    "STREAK" -> ActivityItem.StreakItem(
        id = id,
        createdAt = createdAt,
        streakDays = streakDays ?: 0,
    )

    else -> null
}

fun ActivityResponseDto.toDomain(): ActivityData = ActivityData(
    weeklySpotScore = weeklySpotScore,
    todayInteractions = todayInteractions,
    items = items.mapNotNull { it.toDomain() },
)
