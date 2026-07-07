package com.revio.app.data.repository

import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.safeApiCall
import com.revio.app.data.remote.api.LeaderboardApi
import com.revio.app.data.remote.dto.leaderboard.toDomain
import com.revio.app.features.leaderboard.LeaderboardResult
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
