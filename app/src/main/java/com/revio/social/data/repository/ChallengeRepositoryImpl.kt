package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.map
import com.revio.social.core.network.safeApiCall
import com.revio.social.data.model.Challenge
import com.revio.social.data.model.ChallengeProgressDetail
import com.revio.social.data.model.CurrentChallenge
import com.revio.social.data.model.MyChallenges
import com.revio.social.data.remote.api.ChallengeApi
import com.revio.social.data.remote.dto.challenge.toDomain
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface ChallengeRepository {
    suspend fun getCurrentChallenge(): ApiResult<CurrentChallenge>
    suspend fun getChallenge(challengeId: UUID): ApiResult<Challenge>
    suspend fun getChallengeProgress(challengeId: UUID): ApiResult<ChallengeProgressDetail>
    suspend fun getMyChallenges(
        limit: Int,
        cursorEndsAt: Instant? = null,
        cursorId: UUID? = null,
        filter: String? = null,
    ): ApiResult<MyChallenges>
}

@Singleton
class ChallengeRepositoryImpl @Inject constructor(
    private val challengeApi: ChallengeApi,
) : ChallengeRepository {

    override suspend fun getCurrentChallenge(): ApiResult<CurrentChallenge> {
        return safeApiCall { challengeApi.getCurrentChallenge() }.map { it.toDomain() }
    }

    override suspend fun getChallenge(challengeId: UUID): ApiResult<Challenge> {
        return safeApiCall { challengeApi.getChallenge(challengeId) }.map { it.toDomain() }
    }

    override suspend fun getChallengeProgress(challengeId: UUID): ApiResult<ChallengeProgressDetail> {
        return safeApiCall { challengeApi.getChallengeProgress(challengeId) }.map { it.toDomain() }
    }

    override suspend fun getMyChallenges(
        limit: Int,
        cursorEndsAt: Instant?,
        cursorId: UUID?,
        filter: String?,
    ): ApiResult<MyChallenges> {
        return safeApiCall {
            challengeApi.getMyChallenges(
                limit = limit,
                cursorEndsAt = cursorEndsAt?.toString(),
                cursorId = cursorId?.toString(),
                filter = filter,
            )
        }.map { it.toDomain() }
    }
}
