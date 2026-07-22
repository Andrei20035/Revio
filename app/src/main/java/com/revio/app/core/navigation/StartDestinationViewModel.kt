package com.revio.app.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.app.core.tour.TourController
import com.revio.app.data.local.preferences.TourStatus
import com.revio.app.data.local.preferences.UserPreferences
import com.revio.app.data.local.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val tokenStore: TokenStore? = null,
    private val tourController: TourController? = null,
) : ViewModel() {
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val onboardingDone = userPreferences.onboardingCompleted.first()
            val tokens = tokenStore?.read()
            val legacyToken = if (tokenStore == null) userPreferences.authToken.first() else null
            if (tokenStore != null) userPreferences.removeLegacyJwt()
            val userId = userPreferences.userId.first()

            _startDestination.value = when {
                !onboardingDone -> Screen.Onboarding.route
                tokens == null && legacyToken.isNullOrBlank() -> Screen.Auth.route
                userId == null -> {
                    tokenStore?.clear()
                    userPreferences.clearAuthData()
                    Screen.Auth.route
                }
                else -> {
                    // Valid session on this device. Grandfather pre-existing users (key
                    // absent) straight to Completed so the tour never surprises them, then
                    // let the controller resume an already-armed tour from Feed.
                    if (tourController != null) {
                        if (userPreferences.tourStatus.first() == TourStatus.Unknown) {
                            userPreferences.setTourStatus(TourStatus.Completed)
                        }
                        tourController.startIfArmed()
                    }
                    Screen.Feed.route
                }
            }
        }
    }
}
