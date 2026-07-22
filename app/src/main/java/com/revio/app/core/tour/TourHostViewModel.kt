package com.revio.app.core.tour

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hoists the singleton [TourController] into the composition, mirroring `SessionHostViewModel`. */
@HiltViewModel
class TourHostViewModel @Inject constructor(
    val tourController: TourController,
) : ViewModel()
