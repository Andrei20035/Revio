package com.revio.app.data.remote.dto.leaderboard

import com.revio.app.core.network.serialization.UUIDSerializer
import com.revio.app.features.leaderboard.CurrentUserStanding
import com.revio.app.features.leaderboard.LeaderboardEntry
import com.revio.app.features.leaderboard.LeaderboardResult
import com.revio.app.features.leaderboard.RankMovement
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class LeaderboardEntryDto(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val rank: Int,
    val username: String,
    val avatarUrl: String?,
    val spotScore: Int,
    val streakDays: Int,
)

@Serializable
data class CurrentUserStandingDto(
    val entry: LeaderboardEntryDto,
    val movement: String = "KEEP",
    val placesMoved: Int = 0,
)

@Serializable
data class LeaderboardResponseDto(
    val currentUser: CurrentUserStandingDto,
    val entries: List<LeaderboardEntryDto>,
)

fun LeaderboardEntryDto.toDomain() = LeaderboardEntry(
    userId = userId,
    rank = rank,
    username = username,
    avatarUrl = avatarUrl,
    spotScore = spotScore,
    streakDays = streakDays,
)

fun CurrentUserStandingDto.toDomain() = CurrentUserStanding(
    entry = entry.toDomain(),
    movement = when (movement.uppercase()) {
        "UP" -> RankMovement.UP
        "DOWN" -> RankMovement.DOWN
        else -> RankMovement.KEEP
    },
    placesMoved = placesMoved,
)

fun LeaderboardResponseDto.toDomain() = LeaderboardResult(
    currentUser = currentUser.toDomain(),
    entries = entries.map { it.toDomain() },
)
