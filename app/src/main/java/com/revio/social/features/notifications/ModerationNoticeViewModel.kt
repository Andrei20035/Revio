package com.revio.social.features.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * Drives [ModerationNoticeHost]: fetches unread blocking notifications and hands them out one at
 * a time. [checkForNotices] is meant to be called whenever the app reaches Feed — both a cold
 * start with an existing session and the moment right after a successful login land there.
 */
@HiltViewModel
class ModerationNoticeViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
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
                is ApiResult.Error -> Unit
            }
        }
    }

    fun acknowledgeCurrent() {
        val current = _pending.value.firstOrNull() ?: return
        viewModelScope.launch {
            notificationRepository.markRead(current.id)
            _pending.value = _pending.value.drop(1)
        }
    }
}
