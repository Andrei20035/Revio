package com.revio.app.data.remote.dto.auth

import com.revio.app.data.model.AuthProvider
import kotlinx.serialization.Serializable

@Serializable
data class DeletionContextDto(
    val provider: AuthProvider,
    val postCount: Int,
    val likesReceived: Int,
    val leaderboardRank: Int? = null,
    val streakDays: Int,
    val accountAgeDays: Int,
)
