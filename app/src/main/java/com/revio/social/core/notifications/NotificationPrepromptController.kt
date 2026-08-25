package com.revio.social.core.notifications

import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.data.local.preferences.UserPreferences
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MAX_SHOWN_COUNT = 3
private val RESHOW_COOLDOWN: Duration = Duration.ofDays(7)

/** Ev. push_preprompt_shown (§16, pas 7.2) — the fallback D card became visible. */
private const val EVENT_PREPROMPT_SHOWN = "push_preprompt_shown"

/** Ev. push_preprompt_result (§16, pas 7.2) — how the user resolved the card. */
private const val EVENT_PREPROMPT_RESULT = "push_preprompt_result"

/** Ev. push_permission_requested (§16, pas 7.2) — the OS permission dialog was invoked from this card. */
private const val EVENT_PERMISSION_REQUESTED = "push_permission_requested"

/** Ev. push_permission_result (§16, pas 7.2) — the OS permission dialog's outcome. */
private const val EVENT_PERMISSION_RESULT = "push_permission_result"

/** Ev. push_settings_opened (§16, pas 7.2) — the CTA sent the user to Android's app-notification settings instead of the OS dialog. */
private const val EVENT_SETTINGS_OPENED = "push_settings_opened"

data class NotificationPrepromptUiState(
    val visible: Boolean = false,
    /** Whether the OS permission dialog was already requested once before — decides the CTA's action (request vs. Settings). */
    val permissionPreviouslyRequested: Boolean = false,
)

/**
 * Fallback pre-prompt "D" (push-notifications plan, §10/step 2.11): the first time a real
 * like/comment is visible in Activity, if the user was never fully enabled on notifications, show
 * the same card style as Variant E — standalone this time, since not every first spot gets
 * engagement to trigger E. Capped at [MAX_SHOWN_COUNT] shows total, at least [RESHOW_COOLDOWN]
 * apart. Pattern mirrors [com.revio.social.core.feedback.FirstPostFeedbackController].
 */
@Singleton
class NotificationPrepromptController @Inject constructor(
    private val userPreferences: UserPreferences,
    private val permissionState: NotificationPermissionState,
    private val clock: Clock,
    private val analyticsClient: AnalyticsClient? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(NotificationPrepromptUiState())
    val uiState: StateFlow<NotificationPrepromptUiState> = _uiState.asStateFlow()

    // In-memory guard — a given engagement observation only ever triggers an eligibility check once per process.
    private var checkedThisSession = false

    /**
     * Called once real like/comment engagement is visible (e.g. from
     * [com.revio.social.features.activity.ActivityViewModel]). No-op if already checked this
     * session, or if the user isn't eligible (already fully enabled, capped out, or still
     * cooling down from the last show).
     */
    fun onEngagementObserved() {
        if (checkedThisSession) return
        checkedThisSession = true
        scope.launch {
            val userId = userPreferences.userId.first() ?: return@launch
            if (!isEligible(userId)) return@launch
            userPreferences.recordNotificationPrepromptShown(userId)
            _uiState.value = NotificationPrepromptUiState(
                visible = true,
                permissionPreviouslyRequested = userPreferences.notificationPermissionRequested.first(),
            )
            val showIndex = userPreferences.notificationPrepromptShownCount(userId).first()
            analyticsClient?.log(
                AnalyticsEvent(
                    name = EVENT_PREPROMPT_SHOWN,
                    params = mapOf(
                        "variant" to AnalyticsParamValue.StringValue("d"),
                        "surface" to AnalyticsParamValue.StringValue("activity"),
                        "show_index" to AnalyticsParamValue.LongValue(showIndex.toLong()),
                    ),
                )
            )
        }
    }

    /** The card's primary CTA ("Notify me") was tapped — logs acceptance regardless of the OS-level outcome that follows. */
    fun onAccepted() {
        logResult(outcome = "accepted")
    }

    private suspend fun isEligible(userId: UUID): Boolean {
        if (permissionState.areNotificationsEnabled()) return false

        val shownCount = userPreferences.notificationPrepromptShownCount(userId).first()
        if (shownCount >= MAX_SHOWN_COUNT) return false

        val lastShownAt = userPreferences.notificationPrepromptLastShownAt(userId).first() ?: return true
        return Duration.between(lastShownAt, clock.instant()) >= RESHOW_COOLDOWN
    }

    /**
     * The OS permission dialog was requested from this prompt's CTA and has returned [granted].
     * Logs both [EVENT_PERMISSION_REQUESTED] and [EVENT_PERMISSION_RESULT] — the host only knows
     * about the dialog once its result callback fires, so both are logged together here.
     */
    fun onPermissionRequested(granted: Boolean) {
        scope.launch { userPreferences.setNotificationPermissionRequested() }
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_PERMISSION_REQUESTED,
                params = mapOf("trigger" to AnalyticsParamValue.StringValue("preprompt_d")),
            )
        )
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_PERMISSION_RESULT,
                params = mapOf(
                    "outcome" to AnalyticsParamValue.StringValue(if (granted) "granted" else "denied"),
                ),
            )
        )
    }

    /** Hides the card without logging a result — used after [onAccepted] once its downstream action (permission dialog or Settings) has resolved. */
    fun close() {
        _uiState.value = NotificationPrepromptUiState()
    }

    /** Logged when the CTA sends the user to Android's app-notification settings ([NotificationPrepromptUiState.permissionPreviouslyRequested] branch). */
    fun logSettingsOpened() {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_SETTINGS_OPENED,
                params = mapOf("source" to AnalyticsParamValue.StringValue("preprompt")),
            )
        )
    }

    /** User-initiated rejection — [reason] is `"dismissed"` (tapped "Not now") or `"back"` (system back). */
    fun dismiss(reason: String = "dismissed") {
        logResult(outcome = reason)
        _uiState.value = NotificationPrepromptUiState()
    }

    private fun logResult(outcome: String) {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_PREPROMPT_RESULT,
                params = mapOf("outcome" to AnalyticsParamValue.StringValue(outcome)),
            )
        )
    }
}
