package com.revio.social.features.settings.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.network.ApiResult
import com.revio.social.core.notifications.NotificationPermissionState
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.remote.dto.notification.UpdateNotificationPrefsRequest
import com.revio.social.data.repository.NotificationPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Channel ids from [com.revio.social.core.notifications.createNotificationChannels] (step 2.2). */
private const val CHANNEL_LIKES = "likes"
private const val CHANNEL_COMMENTS = "comments"
private const val CHANNEL_DISCOVERY = "discovery"
private const val CHANNEL_REMINDERS = "reminders"

/** Ev. push_settings_opened (§16, pas 7.2) — a CTA in this screen sent the user to Android Settings. */
private const val EVENT_SETTINGS_OPENED = "push_settings_opened"

/** Ev. push_permission_requested (§16, pas 7.2) — the OS permission dialog was invoked from the system-notifications row. */
private const val EVENT_PERMISSION_REQUESTED = "push_permission_requested"

/** Ev. push_permission_result (§16, pas 7.2) — the OS permission dialog's outcome. */
private const val EVENT_PERMISSION_RESULT = "push_permission_result"

/** Ev. push_category_toggled (§16, pas 7.2) — a category switch was flipped. */
private const val EVENT_CATEGORY_TOGGLED = "push_category_toggled"

/** Ev. push_channel_blocked_detected (§16, pas 7.2) — state 6: a channel just turned up blocked in Android Settings. */
private const val EVENT_CHANNEL_BLOCKED_DETECTED = "push_channel_blocked_detected"

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val notificationPrefsRepository: NotificationPrefsRepository,
    private val permissionState: NotificationPermissionState,
    private val userPreferences: UserPreferences,
    private val analyticsClient: AnalyticsClient? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    init {
        refreshSystemState()
        loadPreferences()
    }

    /** Re-reads permission/channel/toggle state — call on `ON_RESUME` so a trip to Android Settings is reflected (states 5/6). */
    fun refreshSystemState() {
        viewModelScope.launch {
            val userId = userPreferences.userId.first()
            val everRequested = userId?.let { userPreferences.notificationPermissionRequested(it) } ?: false
            val status = when {
                permissionState.areNotificationsEnabled() -> SystemNotificationsStatus.ENABLED
                !permissionState.hasPostNotificationsPermission() && !everRequested -> SystemNotificationsStatus.NOT_ENABLED
                else -> SystemNotificationsStatus.DISABLED
            }
            val likesBlocked = permissionState.isChannelBlocked(CHANNEL_LIKES)
            val commentsBlocked = permissionState.isChannelBlocked(CHANNEL_COMMENTS)
            val discoveryBlocked = permissionState.isChannelBlocked(CHANNEL_DISCOVERY)
            val remindersBlocked = permissionState.isChannelBlocked(CHANNEL_REMINDERS)

            val previous = _uiState.value
            logNewlyBlockedChannel("likes", wasBlocked = previous.likes.blockedByChannel, nowBlocked = likesBlocked)
            logNewlyBlockedChannel("comments", wasBlocked = previous.comments.blockedByChannel, nowBlocked = commentsBlocked)
            logNewlyBlockedChannel("discovery", wasBlocked = previous.discovery.blockedByChannel, nowBlocked = discoveryBlocked)
            logNewlyBlockedChannel("reminders", wasBlocked = previous.reminders.blockedByChannel, nowBlocked = remindersBlocked)

            _uiState.update {
                it.copy(
                    systemStatus = status,
                    likes = it.likes.copy(blockedByChannel = likesBlocked),
                    comments = it.comments.copy(blockedByChannel = commentsBlocked),
                    discovery = it.discovery.copy(blockedByChannel = discoveryBlocked),
                    reminders = it.reminders.copy(blockedByChannel = remindersBlocked),
                )
            }
        }
    }

    private fun logNewlyBlockedChannel(category: String, wasBlocked: Boolean, nowBlocked: Boolean) {
        if (wasBlocked || !nowBlocked) return
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_CHANNEL_BLOCKED_DETECTED,
                params = mapOf("category" to AnalyticsParamValue.StringValue(category)),
            )
        )
    }

    /** Logged right before launching the OS permission dialog from the system-notifications row. */
    fun logPermissionRequested() {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_PERMISSION_REQUESTED,
                params = mapOf("trigger" to AnalyticsParamValue.StringValue("settings_row")),
            )
        )
    }

    /** Persists that the OS permission dialog was shown, so a denial reads as state 2, not state 1, from then on. */
    fun onPermissionRequestResult(granted: Boolean) {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_PERMISSION_RESULT,
                params = mapOf(
                    "outcome" to AnalyticsParamValue.StringValue(if (granted) "granted" else "denied"),
                ),
            )
        )
        viewModelScope.launch {
            userPreferences.userId.first()?.let { userId -> userPreferences.setNotificationPermissionRequested(userId) }
            refreshSystemState()
        }
    }

    /** Logged when a CTA in this screen opens Android's app- or channel-level notification settings. */
    fun logSettingsOpened(source: String) {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_SETTINGS_OPENED,
                params = mapOf("source" to AnalyticsParamValue.StringValue(source)),
            )
        )
    }

    fun setLikesEnabled(enabled: Boolean) = updateCategory(
        category = "likes",
        enabled = enabled,
        request = UpdateNotificationPrefsRequest(likesEnabled = enabled),
        optimistic = { it.copy(likes = it.likes.copy(enabled = enabled)) },
        rollback = { it.copy(likes = it.likes.copy(enabled = !enabled)) },
    )

    fun setCommentsEnabled(enabled: Boolean) = updateCategory(
        category = "comments",
        enabled = enabled,
        request = UpdateNotificationPrefsRequest(commentsEnabled = enabled),
        optimistic = { it.copy(comments = it.comments.copy(enabled = enabled)) },
        rollback = { it.copy(comments = it.comments.copy(enabled = !enabled)) },
    )

    fun setDiscoveryEnabled(enabled: Boolean) = updateCategory(
        category = "discovery",
        enabled = enabled,
        request = UpdateNotificationPrefsRequest(discoveryEnabled = enabled),
        optimistic = { it.copy(discovery = it.discovery.copy(enabled = enabled)) },
        rollback = { it.copy(discovery = it.discovery.copy(enabled = !enabled)) },
    )

    fun setRemindersEnabled(enabled: Boolean) = updateCategory(
        category = "reminders",
        enabled = enabled,
        request = UpdateNotificationPrefsRequest(remindersEnabled = enabled),
        optimistic = { it.copy(reminders = it.reminders.copy(enabled = enabled)) },
        rollback = { it.copy(reminders = it.reminders.copy(enabled = !enabled)) },
    )

    private fun loadPreferences() {
        viewModelScope.launch {
            when (val result = notificationPrefsRepository.getPreferences()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        prefsLoaded = true,
                        likes = it.likes.copy(enabled = result.data.likesEnabled),
                        comments = it.comments.copy(enabled = result.data.commentsEnabled),
                        discovery = it.discovery.copy(enabled = result.data.discoveryEnabled),
                        reminders = it.reminders.copy(enabled = result.data.remindersEnabled),
                    )
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    /** Optimistic toggle + PUT, rolled back on failure — no user-facing error surface for this step. */
    private fun updateCategory(
        category: String,
        enabled: Boolean,
        request: UpdateNotificationPrefsRequest,
        optimistic: (NotificationSettingsUiState) -> NotificationSettingsUiState,
        rollback: (NotificationSettingsUiState) -> NotificationSettingsUiState,
    ) {
        if (!_uiState.value.switchesInteractive) return
        _uiState.update(optimistic)
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_CATEGORY_TOGGLED,
                params = mapOf(
                    "category" to AnalyticsParamValue.StringValue(category),
                    "outcome" to AnalyticsParamValue.StringValue(if (enabled) "on" else "off"),
                ),
            )
        )
        viewModelScope.launch {
            val result = notificationPrefsRepository.updatePreferences(request)
            if (result is ApiResult.Error) {
                _uiState.update(rollback)
            }
        }
    }
}
