package com.revio.social.data.remote.dto.admin

import com.revio.social.core.network.serialization.InstantSerializer
import com.revio.social.core.network.serialization.UUIDSerializer
import com.revio.social.data.model.ModerationReason
import com.revio.social.data.model.ReportReason
import com.revio.social.data.remote.dto.notification.NotificationDto
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// ---------- post removal ----------

@Serializable
data class RemovePostRequestDto(val reason: ModerationReason, val reasonDetails: String? = null)

@Serializable
data class RemovePostResponseDto(
    @Serializable(with = UUIDSerializer::class) val violationId: UUID,
    @Serializable(with = UUIDSerializer::class) val postId: UUID,
)

// ---------- user search / moderation detail ----------

@Serializable
data class BanStateDto(
    val isBanned: Boolean,
    val permanent: Boolean,
    @Serializable(with = InstantSerializer::class) val bannedUntil: Instant? = null,
    val reason: String? = null,
    @Serializable(with = InstantSerializer::class) val bannedAt: Instant? = null,
    @Serializable(with = UUIDSerializer::class) val bannedBy: UUID? = null,
)

@Serializable
data class AdminUserSummaryDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val username: String,
    val email: String,
    val fullName: String,
    val banState: BanStateDto,
)

@Serializable
data class ModerationViolationDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val postId: UUID,
    val postImageKey: String? = null,
    val postCaption: String? = null,
    val reason: ModerationReason,
    val reasonDetails: String? = null,
    @Serializable(with = UUIDSerializer::class) val adminId: UUID,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @Serializable(with = InstantSerializer::class) val revokedAt: Instant? = null,
    @Serializable(with = UUIDSerializer::class) val revokedBy: UUID? = null,
)

@Serializable
data class UserModerationResponseDto(
    val user: AdminUserSummaryDto,
    val activeViolationCount: Long,
    val needsReview: Boolean,
    val violations: List<ModerationViolationDto>,
    val recentNotifications: List<NotificationDto>,
)

@Serializable
data class BanUserRequestDto(
    val durationDays: Int? = null,
    val permanent: Boolean = false,
    val reason: String? = null,
)

// ---------- reports queue ----------

/** Moderation state of a report; must mirror the server's `ReportStatus`. */
@Serializable
enum class ReportStatus {
    PENDING,
    REVIEWED,
    DISMISSED,
}

/** Decision made when resolving a report; must mirror the server's `ModerationDecision`. */
@Serializable
enum class ModerationDecision {
    UPHOLD,
    DISMISS,
}

@Serializable
data class ReportAdminDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val reporterId: UUID,
    @Serializable(with = UUIDSerializer::class) val postId: UUID,
    val reason: ReportReason,
    val status: ReportStatus,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)

@Serializable
data class ResolveReportRequestDto(val decision: ModerationDecision)
