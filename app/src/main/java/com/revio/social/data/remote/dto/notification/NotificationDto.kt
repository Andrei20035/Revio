package com.revio.social.data.remote.dto.notification

import com.revio.social.core.network.serialization.InstantSerializer
import com.revio.social.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
enum class NotificationType {
    POST_REMOVED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_UNSUSPENDED,
    VIOLATION_REVOKED,
    CHALLENGE_REWARD_REVOKED,
    SOCIAL,
}

/**
 * Mirrors the server's `category` column (`user_notifications`, V36). The default is a
 * defensive fallback for a malformed/missing value, not a "server doesn't send this yet" marker
 * — the server does send it. Existing/moderation rows are `ACCOUNT`.
 */
@Serializable
enum class NotificationCategory {
    ACCOUNT,
    LIKES,
    COMMENTS,
    DISCOVERY,
    REMINDERS,
}

@Serializable
data class NotificationDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val type: NotificationType,
    val title: String,
    val body: String,
    val blocking: Boolean,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val readAt: Instant? = null,
    val category: NotificationCategory = NotificationCategory.ACCOUNT,
    /** Target spot for a LIKES/COMMENTS row. Null for a non-social row, or a tombstone (deleted spot). */
    @Serializable(with = UUIDSerializer::class)
    val postId: UUID? = null,
)

@Serializable
data class NotificationListResponseDto(
    val unreadCount: Long,
    val items: List<NotificationDto>,
)
