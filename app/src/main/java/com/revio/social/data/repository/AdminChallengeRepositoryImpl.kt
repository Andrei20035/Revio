package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.map
import com.revio.social.core.network.safeApiCall
import com.revio.social.data.model.AdminChallenge
import com.revio.social.data.model.AdminChallengeFinalizationResult
import com.revio.social.data.model.AdminChallengePage
import com.revio.social.data.model.AdminChallengeRevokeResult
import com.revio.social.data.model.toDomain
import com.revio.social.data.remote.api.AdminChallengeApi
import com.revio.social.data.remote.dto.admin.challenge.CreateChallengeAdminRequestDto
import com.revio.social.data.remote.dto.admin.challenge.RevokeAllRequestDto
import com.revio.social.data.remote.dto.admin.challenge.UpdateChallengeTitleRequestDto
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface AdminChallengeRepository {
    suspend fun createChallenge(
        title: String,
        description: String?,
        targetFamilyId: UUID,
        requiredPosts: Int,
        rewardPoints: Int,
        startsAtLocal: String,
        endsAtLocal: String,
        timezone: String,
    ): ApiResult<AdminChallenge>

    suspend fun listChallenges(
        limit: Int? = null,
        cursorCreatedAt: Instant? = null,
        cursorId: UUID? = null,
        status: String? = null,
    ): ApiResult<AdminChallengePage>

    suspend fun getChallenge(challengeId: UUID): ApiResult<AdminChallenge>

    suspend fun updateChallenge(
        challengeId: UUID,
        title: String,
        description: String?,
        targetFamilyId: UUID,
        requiredPosts: Int,
        rewardPoints: Int,
        startsAtLocal: String,
        endsAtLocal: String,
        timezone: String,
    ): ApiResult<AdminChallenge>

    suspend fun updateChallengeTitle(
        challengeId: UUID,
        title: String,
        description: String?,
    ): ApiResult<AdminChallenge>

    suspend fun publishChallenge(challengeId: UUID): ApiResult<AdminChallenge>

    suspend fun cancelChallenge(challengeId: UUID): ApiResult<AdminChallengeRevokeResult>

    suspend fun revokeAllRewards(challengeId: UUID): ApiResult<AdminChallengeRevokeResult>

    suspend fun finalizeChallenge(challengeId: UUID): ApiResult<AdminChallengeFinalizationResult>
}

@Singleton
class AdminChallengeRepositoryImpl @Inject constructor(
    private val adminChallengeApi: AdminChallengeApi,
) : AdminChallengeRepository {

    override suspend fun createChallenge(
        title: String,
        description: String?,
        targetFamilyId: UUID,
        requiredPosts: Int,
        rewardPoints: Int,
        startsAtLocal: String,
        endsAtLocal: String,
        timezone: String,
    ): ApiResult<AdminChallenge> = safeApiCall {
        adminChallengeApi.createChallenge(
            CreateChallengeAdminRequestDto(
                title = title,
                description = description,
                targetFamilyId = targetFamilyId,
                requiredPosts = requiredPosts,
                rewardPoints = rewardPoints,
                startsAtLocal = startsAtLocal,
                endsAtLocal = endsAtLocal,
                timezone = timezone,
            ),
        )
    }.map { it.toDomain() }

    override suspend fun listChallenges(
        limit: Int?,
        cursorCreatedAt: Instant?,
        cursorId: UUID?,
        status: String?,
    ): ApiResult<AdminChallengePage> = safeApiCall {
        adminChallengeApi.listChallenges(
            limit = limit,
            cursorCreatedAt = cursorCreatedAt?.toString(),
            cursorId = cursorId?.toString(),
            status = status,
        )
    }.map { it.toDomain() }

    override suspend fun getChallenge(challengeId: UUID): ApiResult<AdminChallenge> =
        safeApiCall { adminChallengeApi.getChallenge(challengeId) }.map { it.toDomain() }

    override suspend fun updateChallenge(
        challengeId: UUID,
        title: String,
        description: String?,
        targetFamilyId: UUID,
        requiredPosts: Int,
        rewardPoints: Int,
        startsAtLocal: String,
        endsAtLocal: String,
        timezone: String,
    ): ApiResult<AdminChallenge> = safeApiCall {
        adminChallengeApi.updateChallenge(
            challengeId,
            CreateChallengeAdminRequestDto(
                title = title,
                description = description,
                targetFamilyId = targetFamilyId,
                requiredPosts = requiredPosts,
                rewardPoints = rewardPoints,
                startsAtLocal = startsAtLocal,
                endsAtLocal = endsAtLocal,
                timezone = timezone,
            ),
        )
    }.map { it.toDomain() }

    override suspend fun updateChallengeTitle(
        challengeId: UUID,
        title: String,
        description: String?,
    ): ApiResult<AdminChallenge> = safeApiCall {
        adminChallengeApi.updateChallengeTitle(challengeId, UpdateChallengeTitleRequestDto(title, description))
    }.map { it.toDomain() }

    override suspend fun publishChallenge(challengeId: UUID): ApiResult<AdminChallenge> =
        safeApiCall { adminChallengeApi.publishChallenge(challengeId) }.map { it.toDomain() }

    override suspend fun cancelChallenge(challengeId: UUID): ApiResult<AdminChallengeRevokeResult> =
        safeApiCall { adminChallengeApi.cancelChallenge(challengeId) }.map { it.toDomain() }

    override suspend fun revokeAllRewards(challengeId: UUID): ApiResult<AdminChallengeRevokeResult> =
        safeApiCall {
            adminChallengeApi.revokeAllRewards(challengeId, RevokeAllRequestDto(confirmChallengeId = challengeId))
        }.map { it.toDomain() }

    override suspend fun finalizeChallenge(challengeId: UUID): ApiResult<AdminChallengeFinalizationResult> =
        safeApiCall { adminChallengeApi.finalizeChallenge(challengeId) }.map { it.toDomain() }
}
