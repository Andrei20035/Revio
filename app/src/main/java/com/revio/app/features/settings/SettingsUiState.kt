package com.revio.app.features.settings

import com.revio.app.data.model.User

data class SettingsUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutCompleted: Boolean = false,
)
