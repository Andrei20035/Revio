package com.revio.social.data.remote.dto.auth

import com.revio.social.data.model.AuthProvider
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
