package com.revio.social.core.ui.earlyspotter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.earlyspotter.EarlySpotterCardState
import com.revio.social.core.earlyspotter.EarlySpotterController
import com.revio.social.core.overlay.ActiveOverlay
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.core.tour.TourController
import com.revio.social.data.local.preferences.TourStatus
import com.revio.social.data.local.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hoists the singleton [EarlySpotterController] into the composition (mirroring
 * [com.revio.social.core.tour.TourHostViewModel]) and owns the orchestration between it, the
 * guided tour, and [AppOverlayCoordinator]:
 * - reports the controller's card state into the coordinator so it can never overlap the tour or
 *   the first-post feedback prompt;
 * - arms the tour only once the welcome card is dismissed (the tour's own arming, for everyone
 *   else, still happens right after profile creation — see `ProfileCustomizationViewModel`);
 * - shows the bonus card once the tour finishes.
 */
@HiltViewModel
class EarlySpotterHostViewModel @Inject constructor(
    val earlySpotterController: EarlySpotterController,
    private val tourController: TourController,
    private val userPreferences: UserPreferences,
    private val overlayCoordinator: AppOverlayCoordinator,
) : ViewModel() {

    /** Whether the guided tour overlay must stay hidden — the Early Spotter welcome card outranks it. */
    val isTourBlocked: StateFlow<Boolean> = overlayCoordinator.isBlockedByFlow(ActiveOverlay.Tour)
        .stateIn(viewModelScope, SharingStarted.Eagerly, overlayCoordinator.isBlockedBy(ActiveOverlay.Tour))

    init {
        viewModelScope.launch {
            earlySpotterController.state.collect { state ->
                overlayCoordinator.setActive(ActiveOverlay.EarlySpotterWelcome, state is EarlySpotterCardState.Welcome)
                overlayCoordinator.setActive(ActiveOverlay.EarlySpotterBonus, state is EarlySpotterCardState.Bonus)
            }
        }
        viewModelScope.launch {
            // Skip the first emission (the state at process start) — only a genuine
            // non-null -> null transition means the tour actually ran and finished.
            tourController.step.drop(1).collect { step ->
                if (step == null) earlySpotterController.showBonusIfEligible()
            }
        }
    }

    fun onWelcomeDismissed() {
        earlySpotterController.onWelcomeAcknowledged()
        viewModelScope.launch {
            val userId = userPreferences.userId.first() ?: return@launch
            userPreferences.setTourStatus(userId, TourStatus.Armed)
            tourController.startIfArmed()
        }
    }

    fun onBonusDismissed() = earlySpotterController.onBonusAcknowledged()
}
