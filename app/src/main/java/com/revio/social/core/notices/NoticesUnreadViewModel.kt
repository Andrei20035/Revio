package com.revio.social.core.notices

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin per-screen bridge to the app-scoped [NoticesUnreadController], so both the Activity bell
 * badge and the Notices screen can `hiltViewModel()` it like any other screen ViewModel instead
 * of injecting the `@Singleton` controller directly — while still reading and mutating the one
 * shared instance, not a per-screen copy. Mirrors [com.revio.social.core.activitydot.ActivityDotViewModel].
 */
@HiltViewModel
class NoticesUnreadViewModel @Inject constructor(
    private val controller: NoticesUnreadController,
) : ViewModel() {

    val unreadCount: StateFlow<Long> = controller.unreadCount

    fun onNoticesOpened() = controller.onNoticesOpened()
}
