package com.revio.social.core.notifications

import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.overlay.ActiveOverlay
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.data.local.preferences.UserPreferences
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MAX_SHOWN_COUNT = 3
private val RESHOW_COOLDOWN: Duration = Duration.ofDays(7)

/** Channel id from [createNotificationChannels] — the fallback D card's own copy is always about a like, so this is the one channel [isEligible] checks (step 3.3). */
private const val LIKES_CHANNEL_ID = "likes"

/**
 * Minimum time since [NotificationPrepromptController] was created before the one-shot campaign
 * (step 1.3) may show — avoids racing [com.revio.social.RevioAppUI]'s cold-start spinner
 * (`start == null`), which is still resolving in the same window this controller is constructed.
 * [NotificationPrepromptController.onSessionRestored] waits out whatever remains of this via
 * [NotificationPrepromptController.checkAndShow]'s `initialDelay` rather than being rejected by
 * it — see [NotificationPrepromptController.isEligibleForCampaign]'s doc.
 */
private val CAMPAIGN_COLD_START_GRACE: Duration = Duration.ofSeconds(3)

/** [NotificationPrepromptController.checkAndShow]'s `surface` value for [NotificationPrepromptController.onSessionRestored] — the one-shot campaign (steps 1.1-1.4/5.1). */
private const val SURFACE_UPGRADE_CAMPAIGN = "upgrade_campaign"

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
    private val appOverlayCoordinator: AppOverlayCoordinator,
    private val analyticsClient: AnalyticsClient? = null,
) {
    // `var` so the secondary (test-only) constructor below can swap it out — Hilt's generated
    // code always resolves every parameter of an `@Inject constructor` from the graph and ignores
    // Kotlin default values, so a dispatcher can't be added there without a new binding. The
    // secondary constructor keeps that out of Hilt's path entirely.
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Test-only entry point: lets tests run the [CAMPAIGN_COLD_START_GRACE] delay (step 1.3) in
     * [checkAndShow] against virtual time (e.g. a `TestDispatcher`/`StandardTestDispatcher`)
     * instead of a real multi-second wait. Never invoked by Hilt — only the primary constructor
     * carries `@Inject`.
     */
    constructor(
        userPreferences: UserPreferences,
        permissionState: NotificationPermissionState,
        clock: Clock,
        appOverlayCoordinator: AppOverlayCoordinator,
        analyticsClient: AnalyticsClient?,
        dispatcher: CoroutineDispatcher,
    ) : this(userPreferences, permissionState, clock, appOverlayCoordinator, analyticsClient) {
        scope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    // Proxy for "cold start" (step 1.3) — this controller is a singleton created once per
    // process, so the instant it's constructed is a reasonable approximation.
    private val controllerCreatedAt: Instant = clock.instant()

    private val _uiState = MutableStateFlow(NotificationPrepromptUiState())
    val uiState: StateFlow<NotificationPrepromptUiState> = _uiState.asStateFlow()

    /** Updates [_uiState] and reports the card's active/inactive state to [appOverlayCoordinator] (step 2.1) — mirrors [com.revio.social.core.feedback.FirstPostFeedbackController.setState]. */
    private fun setUiState(value: NotificationPrepromptUiState) {
        _uiState.value = value
        appOverlayCoordinator.setActive(ActiveOverlay.NotificationPreprompt, value.visible)
    }

    // In-memory guard — a given trigger only ever turns into a shown card once per process.
    // Unlike the old scheme, this is claimed only once [checkAndShow] actually reaches
    // `visible = true` (see below) — an eligibility check that comes back ineligible, blocked by
    // a higher overlay, or with no signed-in user must NOT burn the process's one attempt, since
    // none of the other triggers ([onEngagementObserved]/[onLoginObserved]/[onSessionRestored])
    // would otherwise ever get a chance to show the card in that process. Reset in
    // [resetShowState] if the card ends up closing without [onShown] ever having fired for it
    // (e.g. eligibility passed but no host ever actually composed the card) — that attempt still
    // shouldn't cost the process its one check.
    private var checkedThisSession = false

    // Reentrancy guard for [checkAndShow] itself — its eligibility check suspends, so without
    // this two triggers racing in the same process could both pass the (still unclaimed)
    // [checkedThisSession] check and each schedule a `pendingShow`. Only one [checkAndShow] may
    // be actively evaluating at a time; cleared in the `finally` below regardless of outcome.
    private var checkInFlight = false

    // Guards [scheduleOverlayRetry] (step 2.1 follow-up) so a trigger deferred only by a
    // higher-priority overlay gets re-evaluated once that overlay closes, instead of being lost
    // for the rest of the process the way it would if it simply returned. Shared across surfaces
    // — whichever trigger hits the blocked branch first claims the one retry; [checkedThisSession]
    // itself is deliberately left unclaimed by that branch, so the retry reuses the normal
    // [checkAndShow] path (and its own guards) rather than bypassing them.
    private var overlayRetryPending = false

    private data class PendingShow(val userId: UUID, val surface: String)

    // Set by [checkAndShow] alongside `visible = true`; consumed by [onShown] once
    // [NotificationPrepromptHost] actually composes the card. `null`/`false` whenever no show is
    // currently pending or the pending one has already been recorded.
    private var pendingShow: PendingShow? = null
    private var recordedThisShow = false

    /**
     * Called once real like/comment engagement is visible (e.g. from
     * [com.revio.social.features.activity.ActivityViewModel]). No-op if already checked this
     * session, or if the user isn't eligible (already fully enabled, capped out, or still
     * cooling down from the last show).
     */
    fun onEngagementObserved() {
        checkAndShow(surface = "activity")
    }

    /**
     * Called once right after a successful login/register (see
     * [com.revio.social.features.auth.AuthViewModel.handleAuthResult]) — a second trigger for the
     * same fallback D card, since Android 13+ never prompts for [Manifest.permission
     * .POST_NOTIFICATIONS] on its own and a user with no Activity engagement yet would otherwise
     * never see it. Same eligibility gate ([MAX_SHOWN_COUNT] shows total, [RESHOW_COOLDOWN] apart)
     * and same [checkedThisSession] guard as [onEngagementObserved] — whichever of the two fires
     * first in a process "claims" that session's single check.
     */
    fun onLoginObserved() {
        checkAndShow(surface = "login")
    }

    /**
     * Called once a valid stored session is restored on cold start — no explicit login (see
     * [com.revio.social.core.navigation.StartDestinationViewModel], the valid-session branch,
     * step 1.2). This is the only trigger point for a user who stays logged in across an app
     * update: without it, [onLoginObserved] only fires on an explicit login/register, so such a
     * user would otherwise never see this card unless real Activity engagement happens to trigger
     * [onEngagementObserved] first. Uses its own eligibility gate, [isEligibleForCampaign]
     * (step 1.3) — the one-shot campaign has no cap/cooldown of its own, unlike [isEligible].
     * Shares [checkedThisSession] with the other two triggers — whichever fires first in a
     * process claims that session's single check.
     */
    fun onSessionRestored() {
        val elapsedSinceCreation = Duration.between(controllerCreatedAt, clock.instant())
        val remainingGrace =
            if (elapsedSinceCreation < CAMPAIGN_COLD_START_GRACE) {
                CAMPAIGN_COLD_START_GRACE.minus(elapsedSinceCreation)
            } else {
                Duration.ZERO
            }
        checkAndShow(
            surface = SURFACE_UPGRADE_CAMPAIGN,
            initialDelay = remainingGrace,
            isEligible = ::isEligibleForCampaign,
        )
    }

    /**
     * Marks [userId]/[surface] as pending and flips [uiState] to visible — recording the show
     * itself is [onShown]'s job, once it's actually on screen. [isEligible] is pluggable
     * (step 1.3) so [onSessionRestored] can use [isEligibleForCampaign] instead of the fallback D
     * card's own [isEligible]. Also checks [AppOverlayCoordinator.isBlockedBy] (step 2.1) right
     * before showing — something ranked above [ActiveOverlay.NotificationPreprompt] (tour, Early
     * Spotter, first-post feedback) must never have this card drawn on top of it. Deferred there,
     * [scheduleOverlayRetry] takes over and re-runs this same check once the blocking overlay
     * closes, so being blocked costs the trigger a retry rather than the rest of the process.
     *
     * [initialDelay] (step 1.3) is [onSessionRestored]'s way of waiting out the rest of
     * [CAMPAIGN_COLD_START_GRACE] instead of being rejected by it — zero for every other caller,
     * so [onLoginObserved]/[onEngagementObserved] stay instantaneous. Applied before [isEligible]
     * and the overlay check run, so both read the real state at the moment the card is about to
     * show rather than the moment the trigger fired.
     */
    private fun checkAndShow(
        surface: String,
        initialDelay: Duration = Duration.ZERO,
        isEligible: suspend (UUID) -> Boolean = ::isEligible,
    ) {
        if (checkedThisSession || checkInFlight) return
        checkInFlight = true
        scope.launch {
            try {
                if (!initialDelay.isZero) delay(initialDelay.toMillis())
                val userId = userPreferences.userId.first() ?: return@launch
                if (!isEligible(userId)) return@launch
                if (appOverlayCoordinator.isBlockedBy(ActiveOverlay.NotificationPreprompt)) {
                    scheduleOverlayRetry(surface, isEligible)
                    return@launch
                }
                // Only claimed here, once a show is actually about to happen — see
                // [checkedThisSession]'s doc for why an ineligible/blocked/no-user outcome must
                // not reach this line.
                checkedThisSession = true
                pendingShow = PendingShow(userId, surface)
                recordedThisShow = false
                setUiState(
                    NotificationPrepromptUiState(
                        visible = true,
                        permissionPreviouslyRequested = userPreferences.notificationPermissionRequested(userId),
                    )
                )
            } finally {
                checkInFlight = false
            }
        }
    }

    /**
     * Re-evaluates a trigger that [checkAndShow] deferred solely because a higher-priority
     * overlay ([AppOverlayCoordinator.isBlockedBy], step 2.1) was active — without this, a user
     * who lands directly in the tour (or another overlay ranked above
     * [ActiveOverlay.NotificationPreprompt]) at the moment [surface] fired would never see this
     * card again in that process, since [checkedThisSession] is deliberately left unclaimed by
     * that branch but nothing else ever retries it. Waits for the first
     * [AppOverlayCoordinator.isBlockedByFlow] transition to `false` (blocking overlay closed),
     * then re-runs [checkAndShow] for the same [surface]/[isEligible] — exactly once per process,
     * guarded by [overlayRetryPending]. Re-entering [checkAndShow] means every one of its own
     * guards ([checkedThisSession], [checkInFlight], [isEligible], the overlay check itself)
     * applies again, so nothing here bypasses them.
     */
    private fun scheduleOverlayRetry(surface: String, isEligible: suspend (UUID) -> Boolean) {
        if (overlayRetryPending) return
        overlayRetryPending = true
        scope.launch {
            appOverlayCoordinator.isBlockedByFlow(ActiveOverlay.NotificationPreprompt).first { blocked -> !blocked }
            overlayRetryPending = false
            checkAndShow(surface = surface, isEligible = isEligible)
        }
    }

    /**
     * Called by [NotificationPrepromptHost] once the card has actually entered composition — the
     * only point at which a show should count. Records it (increments the per-user counter,
     * stamps the cooldown) and logs [EVENT_PREPROMPT_SHOWN] against the surface that triggered
     * it. Idempotent per pending show — a recomposition of the host must not double-count.
     *
     * For [SURFACE_UPGRADE_CAMPAIGN] specifically, also marks the one-shot campaign consumed
     * (step 1.4) — only here, mirroring step 0.2's reasoning for the counter above: if
     * [isEligibleForCampaign] deferred the campaign (e.g. the tour is armed, blocked by its
     * overlay-active check), [onShown] never fires, so the flag is correctly left unconsumed for
     * a later session — no explicit tour check needed here.
     */
    fun onShown() {
        val pending = pendingShow ?: return
        if (recordedThisShow) return
        recordedThisShow = true
        scope.launch {
            userPreferences.recordNotificationPrepromptShown(pending.userId)
            if (pending.surface == SURFACE_UPGRADE_CAMPAIGN) {
                userPreferences.setNotificationCampaignV1Done(pending.userId)
            }
            val showIndex = userPreferences.notificationPrepromptShownCount(pending.userId).first()
            analyticsClient?.log(
                AnalyticsEvent(
                    name = EVENT_PREPROMPT_SHOWN,
                    params = mapOf(
                        "variant" to AnalyticsParamValue.StringValue("d"),
                        "surface" to AnalyticsParamValue.StringValue(pending.surface),
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

    /**
     * Whether the OS permission dialog has ever been requested before, from any CTA — the same
     * flag this card's own [checkAndShow] reads into [NotificationPrepromptUiState
     * .permissionPreviouslyRequested]. Exposed so other "Notify me"-style CTAs (e.g. Variant E's
     * notifications step, step 0.4) can decide request-vs-Settings through this controller's
     * shared bookkeeping instead of reading [UserPreferences] directly. Reads the flag for the
     * currently signed-in user (step 3.1's per-user key) — `false` with no session.
     */
    suspend fun hasPermissionBeenRequestedBefore(): Boolean {
        val userId = userPreferences.userId.first() ?: return false
        return userPreferences.notificationPermissionRequested(userId)
    }

    /**
     * Notifications being fully enabled at the OS level used to be the whole story here — but a
     * user can have that toggle on while the one channel this card is actually about ("likes",
     * matching its own copy) is individually muted from Android Settings (step 3.3). In that
     * case they currently get nothing relevant and are never asked; extending the gate to also
     * count as eligible there means such a user still sees the card, routed to Settings by the
     * CTA rather than the (already-granted) OS permission dialog.
     */
    private suspend fun isEligible(userId: UUID): Boolean {
        val nothingToFix = permissionState.areNotificationsEnabled() && !permissionState.isChannelBlocked(LIKES_CHANNEL_ID)
        if (nothingToFix) return false

        val shownCount = userPreferences.notificationPrepromptShownCount(userId).first()
        if (shownCount >= MAX_SHOWN_COUNT) return false

        val lastShownAt = userPreferences.notificationPrepromptLastShownAt(userId).first() ?: return true
        return Duration.between(lastShownAt, clock.instant()) >= RESHOW_COOLDOWN
    }

    /**
     * Eligibility gate for the one-shot notifications upgrade campaign (step 1.3) — distinct from
     * [isEligible], which stays the fallback D card's own 3×/7-day cap/cooldown gate used by
     * [onEngagementObserved]/[onLoginObserved]. Every condition is required:
     *  - notifications aren't already enabled at the OS level;
     *  - the campaign hasn't been shown to this user before ([UserPreferences
     *    .notificationCampaignV1Done]);
     *  - onboarding is complete — defensive: [onSessionRestored]'s caller already implies this,
     *    but the gate stays explicit rather than assuming it.
     *
     * [CAMPAIGN_COLD_START_GRACE] is no longer checked here — [checkAndShow]'s `initialDelay`
     * (step 1.3) now waits it out before this gate ever runs, so the campaign is deferred rather
     * than rejected while racing the cold-start spinner.
     *
     * No app-overlay check here anymore — [checkAndShow] itself now gates every trigger, this one
     * included, on [AppOverlayCoordinator.isBlockedBy] (step 2.1) right before showing.
     *
     * Deliberately NOT checked here: per-channel blocking (step 3.3) and permanently-denied
     * detection (step 3.2) — both belong to later steps.
     */
    private suspend fun isEligibleForCampaign(userId: UUID): Boolean {
        if (permissionState.areNotificationsEnabled()) return false
        if (userPreferences.notificationCampaignV1Done(userId).first()) return false
        if (!userPreferences.onboardingCompleted.first()) return false
        return true
    }

    /**
     * The OS permission dialog was requested from this prompt's CTA and has returned [granted].
     * Logs both [EVENT_PERMISSION_REQUESTED] and [EVENT_PERMISSION_RESULT] — the host only knows
     * about the dialog once its result callback fires, so both are logged together here.
     */
    fun onPermissionRequested(granted: Boolean) {
        scope.launch {
            userPreferences.userId.first()?.let { userId -> userPreferences.setNotificationPermissionRequested(userId) }
        }
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_PERMISSION_REQUESTED,
                params = buildMap {
                    put("trigger", AnalyticsParamValue.StringValue("preprompt_d"))
                    pendingShow?.surface?.let { put("surface", AnalyticsParamValue.StringValue(it)) }
                },
            )
        )
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_PERMISSION_RESULT,
                params = buildMap {
                    put("outcome", AnalyticsParamValue.StringValue(if (granted) "granted" else "denied"))
                    pendingShow?.surface?.let { put("surface", AnalyticsParamValue.StringValue(it)) }
                },
            )
        )
    }

    /** Hides the card without logging a result — used after [onAccepted] once its downstream action (permission dialog or Settings) has resolved. */
    fun close() {
        setUiState(NotificationPrepromptUiState())
        resetShowState()
    }

    /**
     * Re-checks the OS permission state on `ON_RESUME` (step 3.4) — a trip to Android Settings
     * (from either CTA branch) grants the permission out-of-band, and the card would otherwise
     * sit there until the user notices and dismisses it themselves. If the card is currently
     * visible and notifications are now enabled, closes it (no result to log — this isn't a CTA
     * tap) and marks the one-shot campaign as succeeded, regardless of which surface actually
     * showed the card: whichever path got the user to grant it, the campaign's goal is met.
     */
    fun onResumed() {
        if (!_uiState.value.visible) return
        if (!permissionState.areNotificationsEnabled()) return
        val userId = pendingShow?.userId
        close()
        if (userId != null) {
            scope.launch { userPreferences.setNotificationCampaignV1Done(userId) }
        }
    }

    /** Logged when the CTA sends the user to Android's app-notification settings ([NotificationPrepromptUiState.permissionPreviouslyRequested] branch). */
    fun logSettingsOpened() {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_SETTINGS_OPENED,
                params = buildMap {
                    put("source", AnalyticsParamValue.StringValue("preprompt"))
                    pendingShow?.surface?.let { put("surface", AnalyticsParamValue.StringValue(it)) }
                },
            )
        )
    }

    /** User-initiated rejection — [reason] is `"dismissed"` (tapped "Not now") or `"back"` (system back). */
    fun dismiss(reason: String = "dismissed") {
        logResult(outcome = reason)
        setUiState(NotificationPrepromptUiState())
        resetShowState()
    }

    private fun logResult(outcome: String) {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_PREPROMPT_RESULT,
                params = buildMap {
                    put("outcome", AnalyticsParamValue.StringValue(outcome))
                    pendingShow?.surface?.let { put("surface", AnalyticsParamValue.StringValue(it)) }
                },
            )
        )
    }

    /**
     * Clears the pending-show bookkeeping on close/dismiss. If [onShown] never fired for it (the
     * card was made eligible but never actually rendered), also un-claims [checkedThisSession] so
     * a later [onEngagementObserved]/[onLoginObserved] in the same process gets another attempt —
     * this show never counted, so it shouldn't cost the process its one check either.
     */
    private fun resetShowState() {
        if (!recordedThisShow) {
            checkedThisSession = false
        }
        pendingShow = null
        recordedThisShow = false
    }
}
