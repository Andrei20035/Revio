package com.revio.social.features.settings.deleteaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.network.ApiResult
import com.revio.social.data.remote.dto.auth.DeleteAccountRequest
import com.revio.social.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ev. — pas 5.8: account deletion outcome, same outcome/failure_code shape as auth_result (pas 2.2b). */
private const val EVENT_AUTH_DELETE_ACCOUNT_RESULT = "auth_delete_account_result"

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val analyticsClient: AnalyticsClient? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    init {
        loadDeletionContext()
    }

    fun onAction(action: DeleteAccountAction) {
        when (action) {
            is DeleteAccountAction.SelectReason -> selectReason(action.reason)
            is DeleteAccountAction.OtherTextChanged -> _uiState.update { it.copy(otherText = action.text) }
            DeleteAccountAction.NextStep -> nextStep()
            DeleteAccountAction.PreviousStep -> previousStep()
            is DeleteAccountAction.PasswordChanged -> _uiState.update {
                it.copy(password = action.value, confirmFieldError = null, generalError = null)
            }
            is DeleteAccountAction.UsernameConfirmationChanged -> _uiState.update {
                it.copy(usernameConfirmation = action.value, confirmFieldError = null, generalError = null)
            }
            DeleteAccountAction.Confirm -> confirmDeletion()
            DeleteAccountAction.KeepAccount -> Unit // navigating away is a UI concern
        }
    }

    private fun loadDeletionContext() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = authRepository.getDeletionContext()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, stats = result.data, provider = result.data.provider)
                }
                // A stats fetch must never block the deletion flow — render Retention without them.
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun selectReason(reason: DeletionReason) {
        _uiState.update {
            it.copy(
                selectedReason = reason,
                otherText = if (reason == DeletionReason.OTHER) it.otherText else "",
            )
        }
    }

    private fun nextStep() {
        val state = _uiState.value
        when (state.currentStep) {
            DeleteAccountStep.Reason -> {
                if (state.selectedReason != null) {
                    _uiState.update { it.copy(currentStep = DeleteAccountStep.Retention) }
                }
            }
            DeleteAccountStep.Retention -> _uiState.update { it.copy(currentStep = DeleteAccountStep.Confirm) }
            DeleteAccountStep.Confirm -> Unit
        }
    }

    private fun previousStep() {
        when (_uiState.value.currentStep) {
            DeleteAccountStep.Reason -> Unit
            DeleteAccountStep.Retention -> _uiState.update { it.copy(currentStep = DeleteAccountStep.Reason) }
            DeleteAccountStep.Confirm -> _uiState.update { it.copy(currentStep = DeleteAccountStep.Retention) }
        }
    }

    private fun confirmDeletion() {
        val state = _uiState.value
        if (state.isDeleting) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isDeleting = true, confirmFieldError = null, generalError = null)
            }

            val request = DeleteAccountRequest(
                password = state.password.ifBlank { null },
                usernameConfirmation = state.usernameConfirmation.ifBlank { null },
                reason = state.selectedReason?.name,
                details = if (state.selectedReason == DeletionReason.OTHER) {
                    state.otherText.ifBlank { null }
                } else {
                    null
                },
            )

            val result = authRepository.deleteAccount(request)
            analyticsClient?.log(
                AnalyticsEvent(
                    name = EVENT_AUTH_DELETE_ACCOUNT_RESULT,
                    params = buildMap {
                        put("outcome", AnalyticsParamValue.StringValue(if (result is ApiResult.Success) "success" else "failure"))
                        if (result is ApiResult.Error) {
                            put("failure_code", AnalyticsParamValue.StringValue(result.code ?: "unknown"))
                        }
                    },
                )
            )
            when (result) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isDeleting = false, deletionCompleted = true)
                }
                is ApiResult.Error -> _uiState.update { current ->
                    applyError(current, result.code, result.message)
                }
            }
        }
    }

    private fun applyError(state: DeleteAccountUiState, code: String?, message: String): DeleteAccountUiState {
        return when (code) {
            "INVALID_CURRENT_PASSWORD",
            "USERNAME_CONFIRMATION_MISMATCH",
            "VALIDATION_ERROR" -> state.copy(isDeleting = false, confirmFieldError = message)
            else -> state.copy(isDeleting = false, generalError = message)
        }
    }
}
