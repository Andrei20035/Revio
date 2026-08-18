package com.revio.social.features.auth

import com.revio.social.data.remote.dto.auth.WaitlistPrefillDTO

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoginMode: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val errorId: Long = 0L,
    val accountSuspendedMessage: String? = null,
    val navigationEvent: AuthNavigationEvent? = null,
)

sealed class AuthNavigationEvent {
    data class ToProfileCustomization(val waitlistPrefill: WaitlistPrefillDTO? = null) : AuthNavigationEvent()
    object ToFeed : AuthNavigationEvent()
}