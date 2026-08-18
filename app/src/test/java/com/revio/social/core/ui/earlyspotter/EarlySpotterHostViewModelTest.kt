package com.revio.social.core.ui.earlyspotter

import com.revio.social.MainDispatcherRule
import com.revio.social.core.earlyspotter.EarlySpotterCardState
import com.revio.social.core.earlyspotter.EarlySpotterController
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.core.tour.TourController
import com.revio.social.core.tour.TourStep
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class EarlySpotterHostViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * tourController.step starts at null with no real tour run (drop(1) on this initial
     * emission is exactly what filters out process-start noise — see the ViewModel's init doc).
     * Without a genuine non-null -> null transition afterward, showCardIfEligible() must never
     * be called, or a user who never gets a tour would still see the combined card.
     */
    @Test
    fun `tourController step null at start never shows the card without a real tour run`() = runTest {
        val earlySpotterController = mockk<EarlySpotterController>(relaxed = true)
        every { earlySpotterController.state } returns MutableStateFlow(EarlySpotterCardState.Hidden)
        val tourController = mockk<TourController>(relaxed = true)
        every { tourController.step } returns MutableStateFlow<TourStep?>(null)
        val overlayCoordinator = mockk<AppOverlayCoordinator>(relaxed = true)

        EarlySpotterHostViewModel(earlySpotterController, tourController, overlayCoordinator)

        verify(exactly = 0) { earlySpotterController.showCardIfEligible() }
    }
}
