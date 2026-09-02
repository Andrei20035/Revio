package com.revio.social.core.notices

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.core.network.onReconnected
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.remote.dto.notification.NotificationCategory
import com.revio.social.data.repository.NotificationRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How often [NoticesUnreadController.refresh] is allowed to hit the network when not [force]d. */
private val NOTICES_UNREAD_REFRESH_THROTTLE: Duration = Duration.ofMinutes(2)

/**
 * Owns the yellow dot's "are there unread Notices?" state — the single source of truth for both
 * the bell badge in Activity and the Notices screen itself, so the two never diverge the way two
 * independent `NotificationsViewModel` instances used to (plan §2/§3.3). Singleton for the same
 * reason [com.revio.social.core.activitydot.ActivityDotController] is: this state spans
 * navigation destinations and must outlive any single screen's ViewModel scope.
 *
 * Unlike the activity dot (a local watermark comparison), unread-ness here is server-side truth:
 * [refresh] reads `unreadCount` for the ACCOUNT category, and [onNoticesOpened] tells the server
 * to mark every ACCOUNT notice read (excluding blocking notices — see
 * [com.revio.social.features.notifications.ModerationNoticeViewModel], which is the only
 * acknowledgement path for those). The UI zeroes optimistically the instant [onNoticesOpened] is
 * called; if the server call fails, the dot is deliberately NOT re-lit — a failed read-all must
 * never look worse than doing nothing — but the mark-all-read is queued for a single retry on the
 * next reconnect, so the server is never left permanently inconsistent with what the UI showed.
 */
@Singleton
class NoticesUnreadController @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val userPreferences: UserPreferences,
    private val clock: Clock,
    private val connectivity: NetworkConnectivityManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private var currentUserId: UUID? = null
    private var lastFetchAt: Instant? = null
    // Set when a markAllRead(ACCOUNT) call fails — retried once on the next reconnect, so a
    // transient failure never leaves the server's read state permanently behind what the UI
    // already showed (zeroed) to the user.
    private var pendingMarkAllRead: Boolean = false

    private val _unreadCount = MutableStateFlow(0L)
    val unreadCount: StateFlow<Long> = _unreadCount.asStateFlow()

    init {
        // App start (and any user switch): first non-null userId re-evaluates from scratch.
        scope.launch {
            userPreferences.userId.distinctUntilChanged().collect { userId ->
                mutex.withLock {
                    // A different (or no) user must never inherit the previous account's unread
                    // state — reset everything in memory before re-evaluating for the new one.
                    currentUserId = userId
                    lastFetchAt = null
                    pendingMarkAllRead = false
                    _unreadCount.value = 0L
                }
                if (userId != null) refresh(force = true)
            }
        }
        // Reconnect: retry a mark-all-read the UI already showed as done but the server never
        // received, then refresh — data fetched while offline (or never fetched) is almost
        // certainly stale, so this bypasses the throttle regardless of last fetch time.
        scope.launch {
            connectivity.onReconnected().collect {
                mutex.withLock {
                    val userId = currentUserId
                    if (userId != null && pendingMarkAllRead) {
                        attemptMarkAllReadLocked(userId)
                    }
                }
                refresh(force = true)
            }
        }
    }

    /**
     * Fetches the ACCOUNT-category unread count. Throttled to at most once every
     * [NOTICES_UNREAD_REFRESH_THROTTLE] unless [force] is set (e.g. on reconnect, or right after
     * a user switch). A network error leaves the previous state untouched; never throws.
     */
    suspend fun refresh(force: Boolean = false) {
        mutex.withLock {
            val userId = currentUserId ?: return@withLock
            val now = clock.instant()
            val throttled = !force &&
                lastFetchAt?.let { Duration.between(it, now) < NOTICES_UNREAD_REFRESH_THROTTLE } == true
            if (throttled) return@withLock
            lastFetchAt = now

            when (val result = notificationRepository.getNotifications(category = NotificationCategory.ACCOUNT)) {
                is ApiResult.Success -> _unreadCount.value = result.data.unreadCount
                is ApiResult.Error -> Unit
            }
        }
    }

    /**
     * Called when the user opens the Notices screen. Zeroes [unreadCount] immediately
     * (optimistic — the UI must not wait on the network to stop showing the dot), then tells the
     * server to mark every unread ACCOUNT notice read. On failure the dot stays off (never
     * re-lit from a failed request) but the mark-all-read is queued for retry on reconnect.
     */
    fun onNoticesOpened() {
        _unreadCount.value = 0L
        scope.launch {
            mutex.withLock {
                val userId = currentUserId ?: return@withLock
                attemptMarkAllReadLocked(userId)
            }
        }
    }

    /** Must only be called while holding [mutex]. */
    private suspend fun attemptMarkAllReadLocked(userId: UUID) {
        when (notificationRepository.markAllRead(category = NotificationCategory.ACCOUNT)) {
            is ApiResult.Success -> pendingMarkAllRead = false
            is ApiResult.Error -> pendingMarkAllRead = true
        }
    }
}
