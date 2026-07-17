package com.revio.app.features.settings.changepassword

enum class ChangePasswordField {
    CURRENT,
    NEW,
    CONFIRM,
}

data class ChangePasswordUiState(
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",

    val isOldVisible: Boolean = false,
    val isNewVisible: Boolean = false,
    val isConfirmVisible: Boolean = false,

    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,

    val oldPasswordError: String? = null,
    val newPasswordError: String? = null,
    val confirmPasswordError: String? = null,
    val generalError: String? = null,
) {
    val isSaveBlocked: Boolean
        get() = isSaving ||
            oldPassword.isBlank() ||
            newPassword.isBlank() ||
            confirmPassword.isBlank() ||
            newPassword != confirmPassword ||
            !newPasswordMeetsRequirements(newPassword)
}

fun newPasswordMeetsRequirements(password: String): Boolean {
    if (password.length < 8) return false
    val hasUpperCase = password.any { it.isUpperCase() }
    val hasLowerCase = password.any { it.isLowerCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSpecialChar = password.any { !it.isLetterOrDigit() }
    return hasUpperCase && hasLowerCase && hasDigit && hasSpecialChar
}
