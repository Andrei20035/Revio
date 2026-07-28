package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.features.leaderboard.LeaderboardResult

interface LeaderboardRepository {
    suspend fun getLeaderboard(): ApiResult<LeaderboardResult>
}
