package com.revio.app.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                else -> Screen.Feed.route
            }
        }
    }
}
