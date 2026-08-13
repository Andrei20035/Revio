package com.revio.social.data.model

import com.revio.social.data.remote.dto.admin.challenge.ChallengeAdminDto
import com.revio.social.data.remote.dto.admin.challenge.ChallengeAdminPageDto
import com.revio.social.data.remote.dto.admin.challenge.ChallengeListCursorDto
import com.revio.social.data.remote.dto.admin.challenge.FinalizationResultDto
import com.revio.social.data.remote.dto.admin.challenge.RevokeResultDto
import java.time.Instant
import java.util.UUID

/**
 * The persisted lifecycle state of a challenge, as the admin API reports it — not the derived
 * `EffectiveChallengeStatus` (ACTIVE/ENDED) computed client- or server-side from the time window.
 * Mirrors the server's `ChallengeStatus`.
 */
enum class ChallengeAdminStatus {
    DRAFT,
    SCHEDULED,
    CANCELLED,

    /** Any value the server sends that this client doesn't recognize yet. */
    UNKNOWN,
}

/** Domain model for the admin-facing view of a challenge. Mirrors the server's `ChallengeAdminDTO`. */
data class AdminChallenge(
    val id: UUID,
    val title: String,
    val description: String?,
    val targetFamilyId: UUID,
    val requiredPosts: Int,
    val rewardPoints: Int,
    val startsAt: Instant,
    val endsAt: Instant,
    val adminTimezone: String,
    val status: ChallengeAdminStatus,
    val createdBy: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val publishedAt: Instant?,
    val cancelledAt: Instant?,
    val finalizedAt: Instant?,
)

/** Keyset cursor for `GET /admin/challenges`. Mirrors the server's `ChallengeListCursorDTO`. */
data class AdminChallengeListCursor(
    val lastCreatedAt: Instant,
    val lastChallengeId: UUID,
)

/** Response shape of `GET /admin/challenges`. */
data class AdminChallengePage(
    val challenges: List<AdminChallenge>,
    val nextCursor: AdminChallengeListCursor?,
    val hasMore: Boolean,
)

/** Response shape of `POST /admin/challenges/{id}/cancel` and `.../revoke-all`. */
data class AdminChallengeRevokeResult(
    val revokedCount: Int,
)

/** Response shape of `POST /admin/challenges/{id}/finalize`. */
data class AdminChallengeFinalizationResult(
    val grantedCount: Int,
    val revokedCount: Int,
)

fun ChallengeAdminDto.toDomain(): AdminChallenge = AdminChallenge(
    id = id,
    title = title,
    description = description,
    targetFamilyId = targetFamilyId,
    requiredPosts = requiredPosts,
    rewardPoints = rewardPoints,
    startsAt = startsAt,
    endsAt = endsAt,
    adminTimezone = adminTimezone,
    status = when (status.uppercase()) {
        "DRAFT" -> ChallengeAdminStatus.DRAFT
        "SCHEDULED" -> ChallengeAdminStatus.SCHEDULED
        "CANCELLED" -> ChallengeAdminStatus.CANCELLED
        else -> ChallengeAdminStatus.UNKNOWN
    },
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    publishedAt = publishedAt,
    cancelledAt = cancelledAt,
    finalizedAt = finalizedAt,
)

fun ChallengeListCursorDto.toDomain(): AdminChallengeListCursor = AdminChallengeListCursor(
    lastCreatedAt = lastCreatedAt,
    lastChallengeId = lastChallengeId,
)

fun ChallengeAdminPageDto.toDomain(): AdminChallengePage = AdminChallengePage(
    challenges = challenges.map { it.toDomain() },
    nextCursor = nextCursor?.toDomain(),
    hasMore = hasMore,
)

fun RevokeResultDto.toDomain(): AdminChallengeRevokeResult = AdminChallengeRevokeResult(
    revokedCount = revokedCount,
)

fun FinalizationResultDto.toDomain(): AdminChallengeFinalizationResult = AdminChallengeFinalizationResult(
    grantedCount = grantedCount,
    revokedCount = revokedCount,
)
