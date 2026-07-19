package com.revio.app.features.leaderboard

import java.util.UUID

data class LeaderboardEntry(
    val userId: UUID,
    val rank: Int,
    val username: String,
    val avatarUrl: String?,
    val spotScore: Int,
    val streakDays: Int,
)

enum class RankMovement { UP, DOWN, KEEP }

data class CurrentUserStanding(
    val entry: LeaderboardEntry,
    val movement: RankMovement,
    val placesMoved: Int,
)

data class LeaderboardResult(
    val currentUser: CurrentUserStanding,
    val entries: List<LeaderboardEntry>,
)

data class LeaderboardUiState(
    val currentUser: CurrentUserStanding? = null,
    val podium: List<LeaderboardEntry> = emptyList(),
    val rest: List<LeaderboardEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val navbarAvatarUrl: String? = null,
)
