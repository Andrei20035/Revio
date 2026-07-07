package com.revio.app.data.repository

import com.revio.app.core.network.ApiResult
import com.revio.app.features.leaderboard.CurrentUserStanding
import com.revio.app.features.leaderboard.LeaderboardEntry
import com.revio.app.features.leaderboard.LeaderboardResult
import com.revio.app.features.leaderboard.RankMovement
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject

class FakeLeaderboardRepository @Inject constructor() : LeaderboardRepository {

    override suspend fun getLeaderboard(): ApiResult<LeaderboardResult> {
        delay(800)
        return ApiResult.Success(MOCK_RESULT)
    }

    companion object {
        private val MOCK_RESULT = LeaderboardResult(
            currentUser = CurrentUserStanding(
                entry = LeaderboardEntry(
                    userId = UUID.randomUUID(),
                    rank = 28,
                    username = "you",
                    avatarUrl = null,
                    spotScore = 12_800,
                    streakDays = 5,
                ),
                movement = RankMovement.UP,
                placesMoved = 5,
            ),
            entries = listOf(
                LeaderboardEntry(UUID.randomUUID(), 1, "alexc21",      null, 12_500, 15),
                LeaderboardEntry(UUID.randomUUID(), 2, "iamluke",      null, 10_800, 10),
                LeaderboardEntry(UUID.randomUUID(), 3, "justinr64",    null,  9_950,  8),
                LeaderboardEntry(UUID.randomUUID(), 4, "tommy82",      null,  8_400,  5),
                LeaderboardEntry(UUID.randomUUID(), 5, "charlotte_khan", null, 7_650, 3),
                LeaderboardEntry(UUID.randomUUID(), 6, "nightapex",    null,  6_900,  1),
                LeaderboardEntry(UUID.randomUUID(), 7, "lucas.spots",  null,  6_200,  2),
                LeaderboardEntry(UUID.randomUUID(), 8, "elara.v",      null,  5_500,  4),
                LeaderboardEntry(UUID.randomUUID(), 9, "amara22",      null,  5_220,  1),
                LeaderboardEntry(UUID.randomUUID(), 10, "tommy82",     null,  4_890,  7),
            ),
        )
    }
}
