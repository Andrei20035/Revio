package com.revio.app.data.repository

import com.revio.app.core.network.ApiResult
import com.revio.app.features.leaderboard.LeaderboardResult

interface LeaderboardRepository {
    suspend fun getLeaderboard(): ApiResult<LeaderboardResult>
}
