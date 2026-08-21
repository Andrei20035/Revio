package com.revio.social.core.feedback

import com.revio.social.core.network.ApiResult
import com.revio.social.core.overlay.ActiveOverlay
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.data.local.preferences.CachedPromptState
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.model.FeedbackSurface
import com.revio.social.data.model.PromptStatus
import com.revio.social.data.model.QuickReason
import com.revio.social.data.repository.FeedbackRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Lifecycle of the first-post feedback prompt, from armed-but-hidden through submission. */
sealed interface FirstPostPromptState {
    data object Hidden : FirstPostPromptState
    data object Rating : FirstPostPromptState
    data class Reason(val rating: Int) : FirstPostPromptState
    data class Comment(val rating: Int, val reason: QuickReason) : FirstPostPromptState
    data object Submitting : FirstPostPromptState
    data object Confirmed : FirstPostPromptState
}

private const val SHOW_DELAY_MS = 2500L
private const val MAX_AUTO_SHOWN_COUNT = 2
private const val COOLDOWN_MIN_DAYS = 3L
private const val COOLDOWN_MAX_DAYS = 5L

/**
 * Decides *whether* and *when* the first-post feedback prompt may appear. Owns eligibility
 * (mirroring the server's per-account prompt state locally so a killed process doesn't lose an
 * armed-but-not-yet-shown prompt), the frequency rules (one show per session, at most two
 * automatic shows ever, never twice the same day, a single 3–5 day cooldown reshow), and the
 * blocker-aware 2.5s reveal delay. Rating/reason/comment capture and submission belong to the
 * UI layer built on top of this controller.
 */
@Singleton
class FirstPostFeedbackController @Inject constructor(
    private val postCreationSignal: PostCreationSignal,
    private val feedbackRepository: FeedbackRepository,
    private val userPreferences: UserPreferences,
    private val overlayCoordinator: AppOverlayCoordinator,
    private val analytics: Analytics,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<FirstPostPromptState>(FirstPostPromptState.Hidden)
    val state: StateFlow<FirstPostPromptState> = _state.asStateFlow()

    /** Updates [_state] and reports the card's active/inactive state to [overlayCoordinator]. */
    private fun setState(value: FirstPostPromptState) {
        _state.value = value
        overlayCoordinator.setActive(ActiveOverlay.FirstPostFeedback, value != FirstPostPromptState.Hidden)
    }

    /**
     * Whether something ranked above [ActiveOverlay.FirstPostFeedback] (currently only the guided
     * tour) is on screen — the feedback card must hide while it is.
     */
    val isTourActive: StateFlow<Boolean> = overlayCoordinator.isBlockedByFlow(ActiveOverlay.FirstPostFeedback)
        .stateIn(scope, SharingStarted.Eagerly, overlayCoordinator.isBlockedBy(ActiveOverlay.FirstPostFeedback))

    // In-memory guard — reset per process, enforces "at most one show per session" per account.
    private var sessionShownForUserId: UUID? = null

    private var pendingShowJob: Job? = null

    /**
     * The most recent [PostCreatedEvent] this controller observed — read by
     * [com.revio.social.core.ui.feedback.FirstPostFeedbackCardCoordinator] when building the
     * feedback payload, so `uploadDurationMs`/`retryCount`/`lastErrorCode` reach the server
     * instead of being dropped (pas 2.5c).
     */
    var lastPostCreatedEvent: PostCreatedEvent? = null
        private set

    init {
        scope.launch {
            postCreationSignal.events.collect { event -> handlePostCreated(event) }
        }
        scope.launch {
            // Skip the first emission — that's the state at process start, not an account switch.
            userPreferences.userId.distinctUntilChanged().drop(1).collect { resetForUserChange() }
        }
    }

    private fun resetForUserChange() {
        pendingShowJob?.cancel()
        pendingShowJob = null
        sessionShownForUserId = null
        lastPostCreatedEvent = null
        setState(FirstPostPromptState.Hidden)
    }

    private suspend fun handlePostCreated(event: PostCreatedEvent) {
        lastPostCreatedEvent = event
        val userId = currentUserId() ?: return
        if (sessionShownForUserId == userId) return

        val result = feedbackRepository.getPromptState()
        val remoteState = (result as? ApiResult.Success)?.data ?: return

        userPreferences.setFirstPostFeedbackState(
            userId,
            CachedPromptState(remoteState.status, remoteState.shownCount, remoteState.lastShownAt),
        )

        if (remoteState.status == PromptStatus.SUBMITTED || remoteState.status == PromptStatus.DISMISSED_TWICE) {
            return
        }

        userPreferences.setFirstPostFeedbackArmed(userId, true)
        analytics.log(FeedbackEvent(FeedbackEventName.ELIGIBLE))
        analytics.log(FeedbackEvent(FeedbackEventName.SCHEDULED))
    }

    /**
     * Called by Feed/Profile once their content is settled. Schedules the reveal after
     * [SHOW_DELAY_MS] if the prompt is currently eligible, re-checking [isBlocked] (screen-local
     * blockers: open dialogs/sheets, an active snackbar, an in-flight upload, a sub-RESUMED
     * lifecycle) and the tour overlay both before scheduling and right before revealing.
     */
    fun onSurfaceReady(surface: FeedbackSurface, isBlocked: () -> Boolean) {
        pendingShowJob?.cancel()
        pendingShowJob = scope.launch {
            val userId = currentUserId() ?: return@launch
            if (!isEligibleToShowNow(userId)) return@launch
            if (overlayCoordinator.isBlockedBy(ActiveOverlay.FirstPostFeedback) || isBlocked()) return@launch

            delay(SHOW_DELAY_MS)

            if (overlayCoordinator.isBlockedBy(ActiveOverlay.FirstPostFeedback) || isBlocked()) return@launch
            show(userId, surface)
        }
    }

    /** Cancels a scheduled-but-not-yet-revealed prompt, e.g. on navigation away or backgrounding. */
    fun cancelPendingShow() {
        val job = pendingShowJob
        pendingShowJob = null
        if (job != null && job.isActive) {
            job.cancel()
            analytics.log(FeedbackEvent(FeedbackEventName.ABANDONED_NAVIGATION))
        }
    }

    fun onNotNow() = dismiss(FeedbackEventName.NOT_NOW)

    fun onClosedX() = dismiss(FeedbackEventName.CLOSED_X)

    /**
     * Called once feedback has been submitted — whether via the explicit "Send" action or a
     * partial submit triggered by closing the card early with a rating/reason already chosen.
     * [dismissEventName] is the closing gesture's own funnel event (e.g. [FeedbackEventName.NOT_NOW]
     * or [FeedbackEventName.CLOSED_X]) and is only passed for a partial submit, so the dismiss
     * funnel stays intact alongside the submission.
     */
    fun onSubmitted(dismissEventName: FeedbackEventName? = null) {
        scope.launch {
            dismissEventName?.let { analytics.log(FeedbackEvent(it)) }
            analytics.log(FeedbackEvent(FeedbackEventName.SUBMITTED))

            setState(FirstPostPromptState.Hidden)

            val userId = currentUserId() ?: return@launch
            val cached = userPreferences.firstPostFeedbackState(userId).first()
            userPreferences.setFirstPostFeedbackState(
                userId,
                CachedPromptState(
                    status = PromptStatus.SUBMITTED,
                    shownCount = cached?.shownCount ?: 1,
                    lastShownAt = cached?.lastShownAt ?: clock.instant(),
                ),
            )
            userPreferences.setFirstPostFeedbackArmed(userId, false)
        }
    }

    private fun dismiss(eventName: FeedbackEventName) {
        scope.launch {
            setState(FirstPostPromptState.Hidden)
            analytics.log(FeedbackEvent(eventName))
            feedbackRepository.reportDismissed()

            val userId = currentUserId() ?: return@launch
            val cached = userPreferences.firstPostFeedbackState(userId).first()
            val newStatus = if (cached?.status == PromptStatus.DISMISSED_ONCE) {
                PromptStatus.DISMISSED_TWICE
            } else {
                PromptStatus.DISMISSED_ONCE
            }
            userPreferences.setFirstPostFeedbackState(
                userId,
                CachedPromptState(
                    status = newStatus,
                    shownCount = cached?.shownCount ?: 1,
                    lastShownAt = cached?.lastShownAt ?: clock.instant(),
                ),
            )
        }
    }

    private suspend fun show(userId: UUID, surface: FeedbackSurface) {
        sessionShownForUserId = userId
        userPreferences.setFirstPostFeedbackArmed(userId, false)

        val cached = userPreferences.firstPostFeedbackState(userId).first()
        val isCooldownReshow = cached?.status == PromptStatus.DISMISSED_ONCE
        val newShownCount = ((cached?.shownCount ?: 0) + 1).coerceAtMost(MAX_AUTO_SHOWN_COUNT)

        userPreferences.setFirstPostFeedbackState(
            userId,
            CachedPromptState(
                status = cached?.status ?: PromptStatus.ELIGIBLE,
                shownCount = newShownCount,
                lastShownAt = clock.instant(),
            ),
        )

        setState(FirstPostPromptState.Rating)
        analytics.log(FeedbackEvent(FeedbackEventName.SHOWN, surface = surface.name, showIndex = newShownCount))
        if (isCooldownReshow) {
            analytics.log(FeedbackEvent(FeedbackEventName.RESHOWN_AFTER_COOLDOWN, surface = surface.name))
        }

        feedbackRepository.reportShown()
    }

    private suspend fun isEligibleToShowNow(userId: UUID): Boolean {
        if (sessionShownForUserId == userId) return false

        val cached = userPreferences.firstPostFeedbackState(userId).first()
        if (cached?.status == PromptStatus.SUBMITTED || cached?.status == PromptStatus.DISMISSED_TWICE) {
            return false
        }

        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val lastShownDay = cached?.lastShownAt?.atZone(clock.zone)?.toLocalDate()
        if (lastShownDay != null && lastShownDay == today) return false

        if (userPreferences.firstPostFeedbackArmed(userId).first()) return true

        if (cached?.status == PromptStatus.DISMISSED_ONCE && cached.shownCount == 1) {
            val lastShownAt = cached.lastShownAt ?: return false
            val daysSince = Duration.between(lastShownAt, clock.instant()).toDays()
            return daysSince in COOLDOWN_MIN_DAYS..COOLDOWN_MAX_DAYS
        }

        return false
    }

    private suspend fun currentUserId(): UUID? = userPreferences.userId.first()
}

@Module
@InstallIn(SingletonComponent::class)
object FirstPostFeedbackClockModule {
    @Provides
    fun provideClock(): Clock = Clock.systemUTC()
}
