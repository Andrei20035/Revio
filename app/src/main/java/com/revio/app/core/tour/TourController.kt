package com.revio.app.core.tour

import com.revio.app.data.local.preferences.TourStatus
import com.revio.app.data.local.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the first-time guided tour's step across screen navigations. Held as a singleton
 * because [com.revio.app.core.ui.components.FloatingBottomNav] is re-created on every tab
 * switch and each destination has its own ViewModel scope, so no screen-scoped ViewModel can
 * survive the walk from Feed to the Profile Dashboard.
 */
@Singleton
class TourController @Inject constructor(
    private val userPreferences: UserPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stepOrder = TourStep.entries

    private val _step = MutableStateFlow<TourStep?>(null)
    private var hasStartedThisProcess = false

    /** The tour's current step, or null when no tour is running. */
    val step: StateFlow<TourStep?> = _step.asStateFlow()

    /**
     * Starts the tour on [TourStep.Feed] if the persisted status is [TourStatus.Armed].
     * Idempotent per process — safe to call from multiple places (app start, and right after a
     * fresh signup lands on Feed) since at most one tour start ever happens per process. A call
     * made while the status isn't yet [TourStatus.Armed] does not consume the guard, so a later
     * call made once the status becomes Armed still starts the tour.
     */
    suspend fun startIfArmed() {
        if (hasStartedThisProcess) return
        if (userPreferences.tourStatus.first() == TourStatus.Armed) {
            hasStartedThisProcess = true
            _step.value = TourStep.Feed
        }
    }

    /** Moves to the next step in order. A no-op past the last step or when no tour is running. */
    fun advance() {
        val current = _step.value ?: return
        val nextIndex = stepOrder.indexOf(current) + 1
        if (nextIndex < stepOrder.size) {
            _step.value = stepOrder[nextIndex]
        }
    }

    /** Ends the tour and persists completion so it never runs again. */
    fun completeAndPersist() {
        _step.value = null
        scope.launch {
            userPreferences.setTourStatus(TourStatus.Completed)
        }
    }

    /**
     * Clears the in-memory step on session loss without persisting completion, so the tour
     * resumes from [TourStep.Feed] after the next successful login.
     */
    fun cancelForSessionLoss() {
        _step.value = null
    }
}
