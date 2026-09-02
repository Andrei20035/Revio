package com.revio.social.core.overlay

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exclusive, app-wide overlays, in fixed priority order (highest first): the blocking moderation
 * notice dialog (step 2.2) outranks everything else — it's never suppressed, only ever the thing
 * doing the blocking — then the guided tour outranks the combined Early Spotter card (which only
 * ever shows once the tour finishes), which outranks the first-post feedback prompt, which
 * outranks the notifications pre-prompt (step 2.1) — the fallback D card and the one-shot upgrade
 * campaign, both driven by [com.revio.social.core.notifications.NotificationPrepromptController].
 */
enum class ActiveOverlay {
    Moderation,
    Tour,
    EarlySpotter,
    FirstPostFeedback,
    NotificationPreprompt,
}

/**
 * Arbitrates which of the app's exclusive overlays is allowed to be visible at once, by the fixed
 * priority in [ActiveOverlay]. A singleton for the same reason [com.revio.social.core.tour.TourController]
 * is: overlays span navigation destinations and `FloatingBottomNav` is recreated on every tab
 * switch, so no screen-scoped ViewModel can hold this state.
 *
 * Participants report their own active/inactive state via [setActive] and check [isBlockedBy]
 * (or observe [isBlockedByFlow]) for anything ranked above them before showing themselves.
 */
@Singleton
class AppOverlayCoordinator @Inject constructor() {
    private val active = HashMap<ActiveOverlay, Boolean>().apply {
        ActiveOverlay.entries.forEach { this[it] = false }
    }

    private val _activeOverlay = MutableStateFlow<ActiveOverlay?>(null)

    /** The highest-priority overlay currently active, or null if none is. */
    val activeOverlay: StateFlow<ActiveOverlay?> = _activeOverlay.asStateFlow()

    /** Reports whether [overlay] is currently active. Recomputes [activeOverlay] synchronously. */
    @Synchronized
    fun setActive(overlay: ActiveOverlay, isActive: Boolean) {
        active[overlay] = isActive
        _activeOverlay.value = ActiveOverlay.entries.firstOrNull { active[it] == true }
    }

    /** True if something ranked above [overlay] is currently active — [overlay] must stay hidden. */
    fun isBlockedBy(overlay: ActiveOverlay): Boolean {
        val highestActive = _activeOverlay.value ?: return false
        return highestActive.ordinal < overlay.ordinal
    }

    /** Reactive form of [isBlockedBy], for participants that need to observe blocking changes. */
    fun isBlockedByFlow(overlay: ActiveOverlay): Flow<Boolean> =
        activeOverlay.map { it != null && it.ordinal < overlay.ordinal }
}
