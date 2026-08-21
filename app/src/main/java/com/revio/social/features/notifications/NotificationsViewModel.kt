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

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val connectivity: NetworkConnectivityManager,
    private val analyticsClient: AnalyticsClient? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

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
            fetch()
        }
    }

    fun retry() {
        if (_uiState.value.isLoading) return
        load()
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
                        unreadCount = (state.unreadCount - 1).coerceAtLeast(0),
                    )
                }
                is ApiResult.Error -> {
                    logActionResult("mark_read", result)
                    _uiState.update { it.copy(actionErrorMessage = "Couldn't mark as read") }
                }
            }
        }
    }

    fun markAllRead() {
        if (_uiState.value.unreadCount == 0L) return

        viewModelScope.launch {
            when (val result = notificationRepository.markAllRead()) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(
                        items = state.items.map { if (it.readAt == null) it.copy(readAt = Instant.now()) else it },
                        unreadCount = 0,
                    )
                }
                is ApiResult.Error -> {
                    logActionResult("mark_all_read", result)
                    _uiState.update { it.copy(actionErrorMessage = "Couldn't mark all as read") }
                }
            }
        }
    }

    fun clearActionError() {
        _uiState.update { it.copy(actionErrorMessage = null) }
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
            fetch()
        }
    }

    private suspend fun fetch() {
        when (val result = notificationRepository.getNotifications()) {
            is ApiResult.Success -> _uiState.update {
                it.copy(
                    items = result.data.items,
                    unreadCount = result.data.unreadCount,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                    isOffline = false,
                )
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = result.message,
                    isOffline = result.isNetworkError,
                )
            }
        }
    }
}
