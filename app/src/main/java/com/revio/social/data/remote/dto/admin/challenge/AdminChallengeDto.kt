package com.revio.social.data.remote.dto.admin.challenge

import com.revio.social.core.network.serialization.InstantSerializer
import com.revio.social.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// ---------- requests ----------

/** Body of `POST /admin/challenges` (create draft) and `PUT /admin/challenges/{id}` (full draft edit).
 * Mirrors the server's `CreateChallengeAdminRequest`. [startsAtLocal]/[endsAtLocal] are local
 * (no-offset) ISO-8601 date-times, converted DST-correctly server-side using [timezone]. */
@Serializable
data class CreateChallengeAdminRequestDto(
    val title: String,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class) val targetFamilyId: UUID,
    val requiredPosts: Int,
    val rewardPoints: Int,
    val startsAtLocal: String,
    val endsAtLocal: String,
    val timezone: String,
)

/** Body of `PATCH /admin/challenges/{id}`. Mirrors the server's `UpdateChallengeTitleRequest`. */
@Serializable
data class UpdateChallengeTitleRequestDto(
    val title: String,
    val description: String? = null,
)

/** Body of `POST /admin/challenges/{id}/revoke-all`. [confirmChallengeId] must repeat the path id.
 * Mirrors the server's `RevokeAllRequest`. */
@Serializable
data class RevokeAllRequestDto(
    @Serializable(with = UUIDSerializer::class) val confirmChallengeId: UUID,
)

// ---------- responses ----------

/** Mirrors the server's `ChallengeAdminDTO` — the admin-facing view of a challenge, including
 * lifecycle timestamps not exposed on the public `ChallengeDto`. [status] is a free string on
 * the wire (persisted `ChallengeStatus`, not the derived effective status). */
@Serializable
data class ChallengeAdminDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val title: String,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class) val targetFamilyId: UUID,
    val requiredPosts: Int,
    val rewardPoints: Int,
    @Serializable(with = InstantSerializer::class) val startsAt: Instant,
    @Serializable(with = InstantSerializer::class) val endsAt: Instant,
    val adminTimezone: String,
    val status: String,
    @Serializable(with = UUIDSerializer::class) val createdBy: UUID? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @Serializable(with = InstantSerializer::class) val publishedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val cancelledAt: Instant? = null,
    /** Nullable so a response from an older server (before finalization existed) still deserializes. */
    @Serializable(with = InstantSerializer::class) val finalizedAt: Instant? = null,
)

/** Keyset cursor for `GET /admin/challenges`. Mirrors the server's `ChallengeListCursorDTO`. */
@Serializable
data class ChallengeListCursorDto(
    @Serializable(with = InstantSerializer::class) val lastCreatedAt: Instant,
    @Serializable(with = UUIDSerializer::class) val lastChallengeId: UUID,
)

/** Response of `GET /admin/challenges`. Mirrors the server's `ChallengeAdminPageDTO`. */
@Serializable
data class ChallengeAdminPageDto(
    val challenges: List<ChallengeAdminDto> = emptyList(),
    val nextCursor: ChallengeListCursorDto? = null,
    val hasMore: Boolean,
)

/** Response of `POST /admin/challenges/{id}/cancel` and `.../revoke-all`.
 * Mirrors the server's `RevokeResultDTO`. */
@Serializable
data class RevokeResultDto(val revokedCount: Int)

/** Response of `POST /admin/challenges/{id}/finalize`. Mirrors the server's `FinalizationResultDTO`. */
@Serializable
data class FinalizationResultDto(val grantedCount: Int, val revokedCount: Int)
