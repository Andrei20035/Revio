package com.revio.social.core.activitydot

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin per-screen bridge to the app-scoped [ActivityDotController], so each of the four nav-bar
 * screens (Feed/Leaderboard/Activity/Profile) can `hiltViewModel()` it like any other screen
 * ViewModel instead of injecting the `@Singleton` controller directly — while still reading and
 * mutating the one shared instance, not a per-screen copy.
 */
@HiltViewModel
class ActivityDotViewModel @Inject constructor(
    private val controller: ActivityDotController,
) : ViewModel() {

    val hasUnseenActivity: StateFlow<Boolean> = controller.hasUnseenActivity

    fun onActivityOpened() = controller.onActivityOpened()
}
