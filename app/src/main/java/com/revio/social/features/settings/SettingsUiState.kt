package com.revio.social.features.settings

import com.revio.social.data.model.User

data class SettingsUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutCompleted: Boolean = false,
    val analyticsConsentGranted: Boolean = true,
    val analyticsConsentLoaded: Boolean = false,
)
