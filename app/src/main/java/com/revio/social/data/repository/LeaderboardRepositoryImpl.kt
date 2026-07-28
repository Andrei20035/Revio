package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.safeApiCall
import com.revio.social.data.remote.api.LeaderboardApi
import com.revio.social.data.remote.dto.leaderboard.toDomain
import com.revio.social.features.leaderboard.LeaderboardResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaderboardRepositoryImpl @Inject constructor(
    private val leaderboardApi: LeaderboardApi,
) : LeaderboardRepository {

    override suspend fun getLeaderboard(): ApiResult<LeaderboardResult> {
        return when (val result = safeApiCall { leaderboardApi.getLeaderboard() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }
}
