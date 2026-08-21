package com.revio.social.features.notifications

import com.revio.social.data.remote.dto.notification.NotificationDto

data class NotificationsUiState(
    val items: List<NotificationDto> = emptyList(),
    val unreadCount: Long = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
    /**
     * Transient failure from [NotificationsViewModel.markRead]/[NotificationsViewModel.markAllRead]
     * (pas 5.1) — separate from [errorMessage], which replaces the whole list with a full-screen
     * state. This is shown as a dismissible banner over the still-visible list instead.
     */
    val actionErrorMessage: String? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}
