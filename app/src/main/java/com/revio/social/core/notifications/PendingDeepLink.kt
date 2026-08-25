package com.revio.social.core.notifications

import android.content.Intent
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Intent extra key an FCM push's data payload carries to name its deep-link destination. */
const val EXTRA_DEEP_LINK = "deep_link"

/** Ev. push_opened (§16, pas 7.2) — a push notification's Intent was captured, i.e. the user tapped it. */
private const val EVENT_PUSH_OPENED = "push_opened"

/**
 * Recognised [EXTRA_DEEP_LINK] values (push-notifications plan, steps 2.5-2.8). [LIKE]/[COMMENT]
 * both resolve to Profile + `SeePostOverlay` (D3) — [COMMENT] additionally opens `CommentsSheet`.
 */
enum class DeepLinkDestination(val value: String) {
    FEED("feed"),
    LIKE("like"),
    COMMENT("comment");

    companion object {
        fun fromExtra(value: String?): DeepLinkDestination? = entries.find { it.value == value }
    }
}

/**
 * A destination plus, for [DeepLinkDestination.LIKE]/[DeepLinkDestination.COMMENT], the post it
 * targets — [postId] is null for [DeepLinkDestination.FEED] and for a tombstoned target (the
 * post was deleted before the deep link was consumed).
 */
data class DeepLinkTarget(
    val destination: DeepLinkDestination,
    val postId: UUID? = null,
    val openComments: Boolean = false,
)

private val PENDING_DEEP_LINK_TTL = Duration.ofMinutes(10)

/**
 * App-scoped buffer for "the user tapped a push notification" (or, since step 2.8, an inbox row
 * for a social notification — the same destination, without an Intent). Bridges
 * [com.revio.social.MainActivity] (which sees the push Intent — possibly before the nav graph
 * exists, or before the user is logged in) and `RevioAppUI`/`ProfileDashboardViewModel` (which
 * consume it once a destination is reachable). Same shape as
 * [com.revio.social.core.feedback.PostCreationSignal] — an app-scoped singleton holding state
 * broader than any one screen.
 */
@Singleton
class PendingDeepLink @Inject constructor(
    private val clock: Clock,
    private val analyticsClient: AnalyticsClient? = null,
) {
    private val mutex = Mutex()
    private var pending: Pair<DeepLinkTarget, Instant>? = null

    private val _signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits whenever a new target is buffered, so an already-composed UI can react immediately. */
    val signal: SharedFlow<Unit> = _signal

    /**
     * Reads [intent]'s deep-link extra and buffers it. A missing/unrecognized value (malformed
     * intent, or an Intent with no push extras at all) is silently ignored — never throws.
     */
    suspend fun capture(intent: Intent?) {
        val destination = DeepLinkDestination.fromExtra(intent?.getStringExtra(EXTRA_DEEP_LINK)) ?: return
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_PUSH_OPENED,
                params = mapOf("category" to AnalyticsParamValue.StringValue(destination.value)),
            )
        )
        set(DeepLinkTarget(destination))
    }

    /** Buffers [target] directly — for triggers with no Intent, e.g. an inbox row tap (step 2.8). */
    suspend fun set(target: DeepLinkTarget) {
        mutex.withLock { pending = target to clock.instant() }
        _signal.emit(Unit)
    }

    /** Returns and clears the pending target, unless it's older than [PENDING_DEEP_LINK_TTL]. */
    suspend fun consume(): DeepLinkTarget? = mutex.withLock {
        val (target, capturedAt) = pending ?: return@withLock null
        pending = null
        if (Duration.between(capturedAt, clock.instant()) > PENDING_DEEP_LINK_TTL) null else target
    }
}
