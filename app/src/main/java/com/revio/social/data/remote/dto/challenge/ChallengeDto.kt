package com.revio.social.data.remote.dto.challenge

import com.revio.social.core.network.serialization.InstantSerializer
import com.revio.social.core.network.serialization.UUIDSerializer
import com.revio.social.data.model.Challenge
import com.revio.social.data.model.ChallengeContribution
import com.revio.social.data.model.ChallengeHistoryItem
import com.revio.social.data.model.ChallengeProgress
import com.revio.social.data.model.ChallengeProgressDetail
import com.revio.social.data.model.ChallengeSummary
import com.revio.social.data.model.CurrentChallenge
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.MyChallenges
import com.revio.social.data.model.ParticipantState
import com.revio.social.data.model.RewardState
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Network shape of a challenge's public config. Mirrors the server's `ChallengeDTO`. */
@Serializable
data class ChallengeDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val title: String,
    val description: String? = null,
    val targetFamilyBrand: String,
    val targetFamilyName: String,
    val requiredPosts: Int,
    val rewardPoints: Int,
    @Serializable(with = InstantSerializer::class)
    val startsAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val endsAt: Instant,
)

/** Mirrors the server's `ChallengeProgressDTO`. [rewardState] and [participantState] are free
 * strings on the wire. [participantState] is null both for a client-unrecognized value being
 * omitted upstream and for an older server that doesn't send the field at all. */
@Serializable
data class ChallengeProgressDto(
    val contributionCount: Int,
    val rewardState: String,
    val participantState: String? = null,
)

/** Mirrors the server's `ChallengeContributionDTO`. The last three fields are nullable so a
 * response from an older server (without them) still deserializes correctly. */
@Serializable
data class ChallengeContributionDto(
    @Serializable(with = UUIDSerializer::class)
    val postId: UUID,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    val imageUrl: String? = null,
    val carBrand: String? = null,
    val carModel: String? = null,
)

/** Response of `GET /challenges/current`. Both fields are null when nothing is scheduled. */
@Serializable
data class CurrentChallengeDto(
    val challenge: ChallengeDto? = null,
    val progress: ChallengeProgressDto? = null,
)

/** Response of `GET /challenges/{id}/progress`. */
@Serializable
data class ChallengeProgressDetailDto(
    val progress: ChallengeProgressDto,
    val contributions: List<ChallengeContributionDto> = emptyList(),
)

/** Mirrors the server's `ChallengeSummaryDTO`. */
@Serializable
data class ChallengeSummaryDto(
    val joinedCount: Int,
    val completedCount: Int,
    val pointsEarned: Int,
    val totalContributions: Int,
)

/** Mirrors the server's `ChallengeHistoryItemDTO`. [effectiveStatus] is a free string on the wire. */
@Serializable
data class ChallengeHistoryItemDto(
    val challenge: ChallengeDto,
    val effectiveStatus: String,
    val progress: ChallengeProgressDto,
)

/** Response of `GET /challenges/me`. [summary] is present only on the first page (no cursor given). */
@Serializable
data class MyChallengesDto(
    val summary: ChallengeSummaryDto? = null,
    val challenges: List<ChallengeHistoryItemDto> = emptyList(),
    val hasMore: Boolean,
    @Serializable(with = InstantSerializer::class)
    val nextCursorEndsAt: Instant? = null,
    @Serializable(with = UUIDSerializer::class)
    val nextCursorId: UUID? = null,
)

fun ChallengeDto.toDomain(): Challenge = Challenge(
    id = id,
    title = title,
    description = description,
    targetFamilyBrand = targetFamilyBrand,
    targetFamilyName = targetFamilyName,
    requiredPosts = requiredPosts,
    rewardPoints = rewardPoints,
    startsAt = startsAt,
    endsAt = endsAt,
)

fun ChallengeProgressDto.toDomain(): ChallengeProgress = ChallengeProgress(
    contributionCount = contributionCount,
    rewardState = when (rewardState.uppercase()) {
        "NONE" -> RewardState.NONE
        "GRANTED" -> RewardState.GRANTED
        else -> RewardState.UNKNOWN
    },
    participantState = when (participantState?.uppercase()) {
        "NOT_STARTED" -> ParticipantState.NOT_STARTED
        "IN_PROGRESS" -> ParticipantState.IN_PROGRESS
        "COMPLETED_PENDING" -> ParticipantState.COMPLETED_PENDING
        "REWARDED" -> ParticipantState.REWARDED
        "NOT_COMPLETED" -> ParticipantState.NOT_COMPLETED
        "REVOKED" -> ParticipantState.REVOKED
        "CANCELLED" -> ParticipantState.CANCELLED
        else -> ParticipantState.UNKNOWN
    },
)

fun ChallengeContributionDto.toDomain(): ChallengeContribution = ChallengeContribution(
    postId = postId,
    createdAt = createdAt,
    imageUrl = imageUrl,
    carBrand = carBrand,
    carModel = carModel,
)

fun CurrentChallengeDto.toDomain(): CurrentChallenge = CurrentChallenge(
    challenge = challenge?.toDomain(),
    progress = progress?.toDomain(),
)

fun ChallengeProgressDetailDto.toDomain(): ChallengeProgressDetail = ChallengeProgressDetail(
    progress = progress.toDomain(),
    contributions = contributions.map { it.toDomain() },
)

fun ChallengeSummaryDto.toDomain(): ChallengeSummary = ChallengeSummary(
    joinedCount = joinedCount,
    completedCount = completedCount,
    pointsEarned = pointsEarned,
    totalContributions = totalContributions,
)

fun ChallengeHistoryItemDto.toDomain(): ChallengeHistoryItem = ChallengeHistoryItem(
    challenge = challenge.toDomain(),
    effectiveStatus = when (effectiveStatus.uppercase()) {
        "ACTIVE" -> EffectiveChallengeStatus.ACTIVE
        "ENDED" -> EffectiveChallengeStatus.ENDED
        "SCHEDULED" -> EffectiveChallengeStatus.SCHEDULED
        "CANCELLED" -> EffectiveChallengeStatus.CANCELLED
        else -> EffectiveChallengeStatus.UNKNOWN
    },
    progress = progress.toDomain(),
)

fun MyChallengesDto.toDomain(): MyChallenges = MyChallenges(
    summary = summary?.toDomain(),
    challenges = challenges.map { it.toDomain() },
    hasMore = hasMore,
    nextCursorEndsAt = nextCursorEndsAt,
    nextCursorId = nextCursorId,
)
