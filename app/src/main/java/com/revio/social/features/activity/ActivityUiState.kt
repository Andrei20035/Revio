package com.revio.social.features.activity

import com.revio.social.data.model.User
import com.revio.social.features.activity.model.ActivityItem

data class ActivityUiState(
    val currentUser: User? = null,
    val weeklySpotScore: Int = 0,
    val todayInteractions: Int = 0,
    val items: List<ActivityItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
    val showTodayInteractionsInfo: Boolean = false,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}
