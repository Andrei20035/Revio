package com.revio.social.features.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.core.network.isNetworkError
import com.revio.social.core.network.onReconnected
import com.revio.social.data.remote.dto.notification.NotificationCategory
import com.revio.social.data.remote.dto.notification.NotificationDto
import com.revio.social.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Ev. notification_action_result (pas 5.1) — best-effort actions (Categoria 3), aggregate rate only, never Crashlytics. */
private const val EVENT_NOTIFICATION_ACTION_RESULT = "notification_action_result"

/**
 * Notices is the ACCOUNT-only moderation inbox (post removed, account suspended/reactivated,
 * violation revoked) — social/broadcast notifications (LIKES, COMMENTS, DISCOVERY, REMINDERS)
 * are represented in Activity instead and never fetched here. Unread state is owned by
 * [com.revio.social.core.notices.NoticesUnreadController], not this ViewModel — the screen calls
 * [com.revio.social.core.notices.NoticesUnreadViewModel.onNoticesOpened] on entry instead of
 * exposing a manual "mark all read" action.
 */
@HiltViewModel
class NoticesViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val connectivity: NetworkConnectivityManager,
    private val analyticsClient: AnalyticsClient? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoticesUiState())
    val uiState: StateFlow<NoticesUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            connectivity.onReconnected().collect {
                if (_uiState.value.errorMessage != null) load()
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null, isOffline = false) }
            fetch(reset = true)
        }
    }

    fun retry() {
        if (_uiState.value.isLoading) return
        load()
    }

    /**
     * Called when the screen returns to the foreground (pas 3,
     * docs/plans/avem-un-bug-android-mutable-sky.md) — retries a screen already stuck in a
     * network-error state without depending on the [connectivity] `false -> true` transition
     * this class's own `onReconnected()` collector reacts to, which might never arrive after a
     * stale-cache edge case. Reuses [retry]'s own `isLoading` guard, so this never duplicates a
     * load already in flight.
     */
    fun onResumed() {
        if (_uiState.value.errorMessage != null) retry()
    }

    /** Infinite scroll: appends the next page if there is one and nothing is already in flight. */
    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingMore || state.isLoading || state.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            fetch(reset = false)
        }
    }

    fun markRead(id: UUID) {
        val alreadyRead = _uiState.value.items.firstOrNull { it.id == id }?.readAt != null
        if (alreadyRead) return

        viewModelScope.launch {
            when (val result = notificationRepository.markRead(id)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(
                        items = state.items.map {
                            if (it.id == id) it.copy(readAt = Instant.now()) else it
                        },
                    )
                }
                is ApiResult.Error -> {
                    logActionResult("mark_read", result)
                    _uiState.update { it.copy(actionErrorMessage = "Couldn't mark as read") }
                }
            }
        }
    }

    fun clearActionError() {
        _uiState.update { it.copy(actionErrorMessage = null) }
    }

    /** Notices are exclusively ACCOUNT-category moderation notices — a tap only ever marks read, no deep-link routing. */
    fun onNotificationClicked(notification: NotificationDto) {
        markRead(notification.id)
    }

    private fun logActionResult(action: String, error: ApiResult.Error) {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_NOTIFICATION_ACTION_RESULT,
                params = mapOf(
                    "action" to AnalyticsParamValue.StringValue(action),
                    "outcome" to AnalyticsParamValue.StringValue("failure"),
                    "failure_code" to AnalyticsParamValue.StringValue(error.code ?: "unknown"),
                ),
            )
        )
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isOffline = false) }
            fetch(reset = true)
        }
    }

    /**
     * Fetches a page of ACCOUNT notices. [reset] true replaces [NoticesUiState.items] with the
     * first page; false appends the next page (using the current [NoticesUiState.nextCursor]) —
     * no de-duplication logic is needed since a keyset cursor never repeats a row and
     * `LazyColumn`'s `key = { it.id }` already guards the render.
     */
    private suspend fun fetch(reset: Boolean) {
        val cursor = if (reset) null else _uiState.value.nextCursor
        when (
            val result = notificationRepository.getNotifications(
                category = NotificationCategory.ACCOUNT,
                cursorCreatedAt = cursor?.lastCreatedAt?.toString(),
                cursorNotificationId = cursor?.lastNotificationId?.toString(),
            )
        ) {
            is ApiResult.Success -> _uiState.update { state ->
                // Defense in depth: the server already filters by category, but Notices must
                // never render a non-ACCOUNT row even if a future server bug returns one.
                val page = result.data.items.filter { item -> item.category == NotificationCategory.ACCOUNT }
                state.copy(
                    items = if (reset) page else state.items + page,
                    nextCursor = result.data.nextCursor,
                    hasMore = result.data.hasMore,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage = null,
                    isOffline = false,
                )
            }
            is ApiResult.Error -> _uiState.update { state ->
                if (reset) {
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message,
                        isOffline = result.isNetworkError,
                    )
                } else {
                    // A load-more failure must never clear the already-visible list — just stop the spinner.
                    state.copy(isLoadingMore = false)
                }
            }
        }
    }
}
