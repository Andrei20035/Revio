package com.revio.app.features.activity.model

import java.time.Instant
import java.util.UUID

sealed class ActivityItem {
    abstract val id: String
    abstract val createdAt: Instant

    data class LikeItem(
        override val id: String,
        override val createdAt: Instant,
        val actorUserId: UUID,
        val actorUsername: String,
        val actorAvatarUrl: String?,
        val postId: UUID,
        val postThumbnailUrl: String?,
        val brand: String?,
        val model: String?,
    ) : ActivityItem()

    data class CommentItem(
        override val id: String,
        override val createdAt: Instant,
        val actorUserId: UUID,
        val actorUsername: String,
        val actorAvatarUrl: String?,
        val postId: UUID,
        val postThumbnailUrl: String?,
        val brand: String?,
        val model: String?,
        val commentText: String,
    ) : ActivityItem()

    data class LeaderboardUpItem(
        override val id: String,
        override val createdAt: Instant,
        val placesMoved: Int,
    ) : ActivityItem()

    data class StreakItem(
        override val id: String,
        override val createdAt: Instant,
        val streakDays: Int,
    ) : ActivityItem()
}

data class ActivityData(
    val weeklySpotScore: Int,
    val todayInteractions: Int,
    val items: List<ActivityItem>,
)
