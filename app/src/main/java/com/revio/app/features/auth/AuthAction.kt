package com.revio.app.features.auth

sealed class AuthAction {
    data class EmailChanged(val email: String) : AuthAction()
    data class PasswordChanged(val password: String) : AuthAction()
    data class ConfirmPasswordChanged(val password: String) : AuthAction()
    object TogglePasswordVisibility : AuthAction()
    object ToggleConfirmPasswordVisibility : AuthAction()

    object SubmitEmailAuth : AuthAction()
    data class GoogleSignInResult(val idToken: String?) : AuthAction()

    object ForgotPassword : AuthAction()
    object ToggleMode : AuthAction()

    // For test only
    object ResetOnboarding : AuthAction()
}