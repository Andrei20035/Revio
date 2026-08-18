package com.revio.social.core.ui.earlyspotter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.earlyspotter.EarlySpotterCardState
import com.revio.social.core.earlyspotter.EarlySpotterController
import com.revio.social.core.overlay.ActiveOverlay
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.core.tour.TourController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hoists the singleton [EarlySpotterController] into the composition (mirroring
 * [com.revio.social.core.tour.TourHostViewModel]) and owns the orchestration between it, the
 * guided tour, and [AppOverlayCoordinator]:
 * - reports the controller's card state into the coordinator so it can never overlap the
 *   first-post feedback prompt;
 * - shows the combined card once the tour finishes (the tour's own arming happens right after
 *   profile creation — see `ProfileCustomizationViewModel`).
 */
@HiltViewModel
class EarlySpotterHostViewModel @Inject constructor(
    val earlySpotterController: EarlySpotterController,
    private val tourController: TourController,
    private val overlayCoordinator: AppOverlayCoordinator,
) : ViewModel() {

    init {
        viewModelScope.launch {
            earlySpotterController.state.collect { state ->
                overlayCoordinator.setActive(ActiveOverlay.EarlySpotter, state is EarlySpotterCardState.Visible)
            }
        }
        viewModelScope.launch {
            // Skip the first emission (the state at process start) — only a genuine
            // non-null -> null transition means the tour actually ran and finished.
            tourController.step.drop(1).collect { step ->
                if (step == null) earlySpotterController.showCardIfEligible()
            }
        }
    }

    fun onDismissed() = earlySpotterController.onAcknowledged()
}
