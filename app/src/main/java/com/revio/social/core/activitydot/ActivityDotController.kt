package com.revio.social.core.activitydot

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.core.network.onReconnected
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.remote.dto.notification.NotificationListResponseDto
import com.revio.social.data.repository.NotificationRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How often [ActivityDotController.refresh] is allowed to hit the network when not [force]d. */
private val ACTIVITY_DOT_REFRESH_THROTTLE: Duration = Duration.ofMinutes(2)

/**
 * Owns the red activity dot's "have I seen the latest social activity?" state — the single source
 * of truth for every nav-bar consumer, so Feed/Leaderboard/Activity/Profile never diverge (see
 * revio audit plan §9, "State ownership"). Singleton for the same reason
 * [com.revio.social.core.earlyspotter.EarlySpotterController] is: this state spans navigation
 * destinations and must outlive any single screen's ViewModel scope.
 *
 * Strategy (plan §7/§8, "Strategy C"): the dot lights up when the latest server-side notification
 * `updatedAt` this user has ever produced is newer than the `updatedAt` they last saw by opening
 * Activity — a high-watermark comparison of two *server* timestamps, never the device clock. This
 * is the only strategy that (a) doesn't depend on push, which is off by default in production, and
 * (b) detects an existing aggregated row being updated (a new liker joining a window), not just new
 * rows appearing.
 *
 * Reuses [NotificationRepository] — the same endpoint [com.revio.social.features.notifications.NotificationsViewModel]
 * already calls — deliberately, instead of a new API. [lastSnapshot] keeps the *entire* last
 * response (not just the derived timestamp) so that a future consumer (e.g. `NotificationsViewModel`
 * itself) can read from this controller instead of issuing its own fetch, without this controller
 * needing to change shape — see the audit plan's Task B1.
 */
@Singleton
class ActivityDotController @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val userPreferences: UserPreferences,
    private val clock: Clock,
    private val connectivity: NetworkConnectivityManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private var currentUserId: UUID? = null
    private var latestUpdatedAt: Instant? = null
    private var lastFetchAt: Instant? = null
    // Set when onActivityOpened() runs before any latestUpdatedAt is known (offline, or before
    // the first refresh() has returned). Consumed by the next successful refresh(), which stamps
    // the watermark then instead of recomputing — otherwise that first fetch would light the dot
    // for activity the user had already opened Activity for.
    private var pendingOpen: Boolean = false

    private val _hasUnseenActivity = MutableStateFlow(false)
    val hasUnseenActivity: StateFlow<Boolean> = _hasUnseenActivity.asStateFlow()

    private val _lastSnapshot = MutableStateFlow<NotificationListResponseDto?>(null)
    val lastSnapshot: StateFlow<NotificationListResponseDto?> = _lastSnapshot.asStateFlow()

    init {
        // App start (and any user switch): first non-null userId re-evaluates from scratch.
        scope.launch {
            userPreferences.userId.distinctUntilChanged().collect { userId ->
                mutex.withLock {
                    // A different (or no) user must never inherit the previous account's dot
                    // state — reset everything in memory before re-evaluating for the new one.
                    currentUserId = userId
                    latestUpdatedAt = null
                    lastFetchAt = null
                    pendingOpen = false
                    _lastSnapshot.value = null
                    _hasUnseenActivity.value = false
                }
                if (userId != null) refresh(force = true)
            }
        }
        // Reconnect: data fetched while offline (or never fetched) is almost certainly stale,
        // so this bypasses the throttle regardless of when the last successful fetch was.
        scope.launch {
            connectivity.onReconnected().collect { refresh(force = true) }
        }
    }

    /**
     * Fetches the latest notifications and recomputes [hasUnseenActivity]. Throttled to at most
     * once every [ACTIVITY_DOT_REFRESH_THROTTLE] unless [force] is set (e.g. on reconnect, or
     * right after a user switch) — protects against a caller (e.g. every app-foreground) hitting
     * the network on every call. A network error leaves the previous state untouched; never
     * throws.
     */
    suspend fun refresh(force: Boolean = false) {
        mutex.withLock {
            val userId = currentUserId ?: return@withLock
            val now = clock.instant()
            val throttled = !force &&
                lastFetchAt?.let { Duration.between(it, now) < ACTIVITY_DOT_REFRESH_THROTTLE } == true
            if (throttled) return@withLock
            lastFetchAt = now

            when (val result = notificationRepository.getNotifications()) {
                is ApiResult.Success -> {
                    _lastSnapshot.value = result.data
                    // Truncated to milliseconds to match the precision UserPreferences persists
                    // the watermark at (Instant epoch millis) — otherwise the sub-millisecond
                    // precision of the server's `updated_at` (Postgres TIMESTAMP, microseconds)
                    // makes latestUpdatedAt permanently newer than any persisted lastSeenActivityAt,
                    // and the dot never stays lit off after onActivityOpened().
                    latestUpdatedAt = result.data.items.maxOfOrNull { it.updatedAt }?.truncatedTo(ChronoUnit.MILLIS)
                    if (pendingOpen) {
                        // onActivityOpened() ran before we had a watermark to stamp — do it now,
                        // with this fetch's value, instead of lighting the dot for it.
                        pendingOpen = false
                        val latest = latestUpdatedAt
                        if (latest != null) userPreferences.setActivityLastSeenAt(userId, latest)
                        _hasUnseenActivity.value = false
                    } else {
                        recomputeUnseenLocked(userId)
                    }
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    /**
     * Called when the user opens (or is already on) the Activity destination — via tab, deep
     * link, or navigation restoration alike, and also when the Activity tab is tapped while
     * already selected. Idempotent: repeated calls have no further effect. Stamps
     * [UserPreferences.setActivityLastSeenAt] with the latest *server* `updatedAt` this controller
     * has observed (never [Instant.now]), so the watermark stays comparable across devices/clocks.
     */
    fun onActivityOpened() {
        // Immediate optimistic flip so the UI reacts without waiting on the persist below.
        _hasUnseenActivity.value = false
        scope.launch {
            mutex.withLock {
                val userId = currentUserId ?: return@withLock
                val latest = latestUpdatedAt
                if (latest == null) {
                    // No watermark yet (offline, or before the first refresh() returned) — defer
                    // the persist to the next successful refresh() instead of silently doing
                    // nothing, so this "open" isn't lost once data does arrive.
                    pendingOpen = true
                    return@withLock
                }
                userPreferences.setActivityLastSeenAt(userId, latest)
                // Recompute under the same lock a refresh() might be holding/waiting on, so
                // whichever of the two finishes last leaves the definitive, consistent value.
                recomputeUnseenLocked(userId)
            }
        }
    }

    /** Must only be called while holding [mutex]. */
    private suspend fun recomputeUnseenLocked(userId: UUID) {
        val latest = latestUpdatedAt
        if (latest == null) {
            _hasUnseenActivity.value = false
            return
        }
        val lastSeen = userPreferences.activityLastSeenAt(userId).first()
        _hasUnseenActivity.value = lastSeen == null || latest.isAfter(lastSeen)
    }
}
