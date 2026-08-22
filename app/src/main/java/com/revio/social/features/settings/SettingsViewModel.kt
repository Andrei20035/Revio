package com.revio.social.features.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.applyAnalyticsConsent
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.network.ApiResult
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.repository.UserRepository
import com.revio.social.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ev. — pas 5.8: logout is best-effort server-side (local session always clears — Categoria 3), aggregate rate only. */
private const val EVENT_AUTH_LOGOUT_RESULT = "auth_logout_result"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val appContext: Context,
    private val analyticsClient: AnalyticsClient? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
        viewModelScope.launch {
            userRepository.currentUser.filterNotNull().collect { user ->
                _uiState.update { it.copy(user = user) }
            }
        }
        viewModelScope.launch {
            userPreferences.analyticsConsentGrantedOrNull.collect { granted ->
                _uiState.update {
                    it.copy(analyticsConsentGranted = granted ?: true, analyticsConsentLoaded = true)
                }
            }
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = userRepository.getCurrentUser()) {
                is ApiResult.Success -> _uiState.update { it.copy(user = result.data, isLoading = false) }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Persists the user's choice and applies it immediately; RevioApp restores it at cold start. */
    fun setAnalyticsConsentGranted(granted: Boolean) {
        viewModelScope.launch {
            userPreferences.setAnalyticsConsentGranted(granted)
        }
        applyAnalyticsConsent(appContext, granted)
    }

    fun logout() {
        if (_uiState.value.isLoggingOut) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            val result = authRepository.logout()
            analyticsClient?.log(
                AnalyticsEvent(
                    name = EVENT_AUTH_LOGOUT_RESULT,
                    params = buildMap {
                        put("outcome", AnalyticsParamValue.StringValue(if (result is ApiResult.Success) "success" else "failure"))
                        if (result is ApiResult.Error) {
                            put("failure_code", AnalyticsParamValue.StringValue(result.code ?: "unknown"))
                        }
                    },
                )
            )
            // Local session clears either way (AuthRepositoryImpl.logout) — the server-side
            // revoke failing is best-effort (Categoria 3), never blocks the user from logging
            // out on this device.
            _uiState.update { it.copy(isLoggingOut = false, logoutCompleted = true) }
        }
    }
}
