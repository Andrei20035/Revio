package com.revio.social.features.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.network.ApiResult
import com.revio.social.data.remote.dto.notification.NotificationDto
import com.revio.social.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ev. notification_action_result (pas 5.1) — best-effort actions (Categoria 3), aggregate rate only, never Crashlytics. */
private const val EVENT_NOTIFICATION_ACTION_RESULT = "notification_action_result"

/**
 * Drives [ModerationNoticeHost]: fetches unread blocking notifications and hands them out one at
 * a time. [checkForNotices] is meant to be called whenever the app reaches Feed — both a cold
 * start with an existing session and the moment right after a successful login land there.
 */
@HiltViewModel
class ModerationNoticeViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val analyticsClient: AnalyticsClient? = null,
) : ViewModel() {

    private val _pending = MutableStateFlow<List<NotificationDto>>(emptyList())

    /** The next notice to acknowledge, oldest first, or null when there's nothing pending. */
    val currentNotice: StateFlow<NotificationDto?> = _pending
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun checkForNotices() {
        viewModelScope.launch {
            when (val result = notificationRepository.getNotifications()) {
                is ApiResult.Success -> {
                    _pending.value = result.data.items.filter { it.blocking && it.readAt == null }
                }
                is ApiResult.Error -> logActionResult("check_notices", result)
            }
        }
    }

    fun acknowledgeCurrent() {
        val current = _pending.value.firstOrNull() ?: return
        viewModelScope.launch {
            when (val result = notificationRepository.markRead(current.id)) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> logActionResult("acknowledge", result)
            }
            // Dropped from the local queue either way: a failure here just means this notice may
            // reappear on the next checkForNotices — acceptable for a best-effort bookkeeping call
            // (see ErrorPolicy.SILENT on NotificationRepositoryImpl.markRead), not worth retrying
            // or blocking the user from continuing past the dialog.
            _pending.value = _pending.value.drop(1)
        }
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
}
