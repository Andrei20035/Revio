package com.revio.app.features.settings.changepassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.app.core.network.ApiResult
import com.revio.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onOldPasswordChanged(value: String) {
        _uiState.update { it.copy(oldPassword = value, oldPasswordError = null, generalError = null) }
    }

    fun onNewPasswordChanged(value: String) {
        _uiState.update { it.copy(newPassword = value, newPasswordError = null, generalError = null) }
    }

    fun onConfirmPasswordChanged(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null, generalError = null) }
    }

    fun onToggleOldVisibility() {
        _uiState.update { it.copy(isOldVisible = !it.isOldVisible) }
    }

    fun onToggleNewVisibility() {
        _uiState.update { it.copy(isNewVisible = !it.isNewVisible) }
    }

    fun onToggleConfirmVisibility() {
        _uiState.update { it.copy(isConfirmVisible = !it.isConfirmVisible) }
    }

    fun onSaveSuccessConsumed() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving || state.isSaveBlocked) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveSuccess = false,
                    oldPasswordError = null,
                    newPasswordError = null,
                    confirmPasswordError = null,
                    generalError = null,
                )
            }

            when (val result = authRepository.updatePassword(state.oldPassword, state.newPassword)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                        oldPassword = "",
                        newPassword = "",
                        confirmPassword = "",
                    )
                }

                is ApiResult.Error -> _uiState.update { current ->
                    applyError(current, result.code, result.message)
                }
            }
        }
    }

    private fun applyError(state: ChangePasswordUiState, code: String?, message: String): ChangePasswordUiState {
        return when (code) {
            "INVALID_CURRENT_PASSWORD" -> state.copy(isSaving = false, oldPasswordError = message)
            "WEAK_PASSWORD", "VALIDATION_ERROR" -> state.copy(isSaving = false, newPasswordError = message)
            "PROVIDER_NOT_REGULAR" -> state.copy(
                isSaving = false,
                generalError = "You signed in with Google, so there's no password to change.",
            )
            else -> state.copy(isSaving = false, generalError = message)
        }
    }
}
