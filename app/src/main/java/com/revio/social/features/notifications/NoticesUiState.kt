package com.revio.social.features.notifications

import com.revio.social.data.remote.dto.notification.NotificationCursorDto
import com.revio.social.data.remote.dto.notification.NotificationDto

data class NoticesUiState(
    val items: List<NotificationDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
    /**
     * Transient failure from [NoticesViewModel.markRead] — separate from [errorMessage], which
     * replaces the whole list with a full-screen state. This is shown as a dismissible banner
     * over the still-visible list instead.
     */
    val actionErrorMessage: String? = null,
    /** Keyset cursor for the next page, or null if [hasMore] is false. */
    val nextCursor: NotificationCursorDto? = null,
    val hasMore: Boolean = false,
    /** True while [NoticesViewModel.loadMore] has a request in flight — guards against re-entrant calls. */
    val isLoadingMore: Boolean = false,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}
