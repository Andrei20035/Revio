package com.revio.social.features.notifications

import com.revio.social.data.remote.dto.notification.NotificationDto

data class NotificationsUiState(
    val items: List<NotificationDto> = emptyList(),
    val unreadCount: Long = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}
