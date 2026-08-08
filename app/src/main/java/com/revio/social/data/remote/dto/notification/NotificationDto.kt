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
)

@Serializable
data class NotificationListResponseDto(
    val unreadCount: Long,
    val items: List<NotificationDto>,
)
