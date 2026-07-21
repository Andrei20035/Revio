package com.revio.app.features.activity

import com.revio.app.data.model.User
import com.revio.app.features.activity.model.ActivityItem

data class ActivityUiState(
    val currentUser: User? = null,
    val weeklySpotScore: Int = 0,
    val todayInteractions: Int = 0,
    val items: List<ActivityItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val showTodayInteractionsInfo: Boolean = false,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}
