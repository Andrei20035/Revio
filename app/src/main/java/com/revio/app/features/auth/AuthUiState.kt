package com.revio.app.features.auth

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
    val navigationEvent: AuthNavigationEvent? = null,
)

sealed class AuthNavigationEvent {
    object ToProfileCustomization : AuthNavigationEvent()
    object ToFeed : AuthNavigationEvent()
}