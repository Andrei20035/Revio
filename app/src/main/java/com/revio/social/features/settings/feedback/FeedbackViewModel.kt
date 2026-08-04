package com.revio.social.features.settings.feedback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.device.DeviceInfoProvider
import com.revio.social.core.feedback.Analytics
import com.revio.social.core.feedback.FeedbackEvent
import com.revio.social.core.feedback.FeedbackEventName
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.isNetworkError
import com.revio.social.data.model.FeedbackCategory
import com.revio.social.data.model.FeedbackSource
import com.revio.social.data.model.UserFeedbackPayload
import com.revio.social.data.repository.FeedbackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ARG_SOURCE = "source"
private const val ARG_ORIGIN_SCREEN = "screen"
private const val OFFLINE_MESSAGE = "You're offline. Try again when you're connected."

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val analytics: Analytics,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val source: FeedbackSource =
        savedStateHandle.get<String>(ARG_SOURCE)
            ?.let { runCatching { FeedbackSource.valueOf(it) }.getOrNull() }
            ?: FeedbackSource.SETTINGS_FEEDBACK

    private val originScreen: String? = savedStateHandle.get<String>(ARG_ORIGIN_SCREEN)

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    /** Tracked so `MESSAGE_STARTED` fires once per form, not on every keystroke. */
    private var messageStartedLogged = false

    init {
        analytics.log(FeedbackEvent(name = FeedbackEventName.SCREEN_OPENED, source = source.name))
    }

    fun onAction(action: FeedbackAction) {
        when (action) {
            is FeedbackAction.SelectCategory -> selectCategory(action.category)
            is FeedbackAction.MessageChanged -> messageChanged(action.text)
            is FeedbackAction.SecondaryMessageChanged -> _uiState.update { it.copy(secondaryMessage = action.text) }
            is FeedbackAction.AreaSelected -> _uiState.update { it.copy(area = action.area) }
            is FeedbackAction.QuickReasonSelected -> _uiState.update { it.copy(quickReason = action.reason) }
            is FeedbackAction.PrioritySelected -> _uiState.update { it.copy(priority = action.priority) }
            is FeedbackAction.RatingSelected -> _uiState.update { it.copy(rating = action.rating) }
            is FeedbackAction.KeepMessageChanged -> _uiState.update { it.copy(keepMessage = action.text) }
            is FeedbackAction.ImproveMessageChanged -> _uiState.update { it.copy(improveMessage = action.text) }
            is FeedbackAction.ToggleIncludeDiagnostics -> _uiState.update { it.copy(includeDiagnostics = action.enabled) }
            FeedbackAction.NextStep -> nextStep()
            FeedbackAction.PreviousStep -> previousStep()
            FeedbackAction.Submit -> {
                logCategoryEvent(FeedbackEventName.SEND_PRESSED)
                submit()
            }
            FeedbackAction.Retry -> {
                logCategoryEvent(FeedbackEventName.RETRY_PRESSED)
                submit()
            }
            FeedbackAction.SendAnother -> {
                logCategoryEvent(FeedbackEventName.ANOTHER_STARTED)
                messageStartedLogged = false
                _uiState.update { FeedbackUiState() }
            }
            FeedbackAction.Cancel -> Unit // navigating away is a UI concern
        }
    }

    private fun logCategoryEvent(name: FeedbackEventName) {
        analytics.log(FeedbackEvent(name = name, category = _uiState.value.category?.name, source = source.name))
    }

    private fun selectCategory(category: FeedbackCategory) {
        val previousCategory = _uiState.value.category
        _uiState.update { it.copy(category = category, step = FeedbackStep.Form) }
        messageStartedLogged = false
        val eventName = if (previousCategory == null) {
            FeedbackEventName.CATEGORY_SELECTED
        } else {
            FeedbackEventName.CATEGORY_CHANGED
        }
        analytics.log(FeedbackEvent(name = eventName, category = category.name, source = source.name))
    }

    private fun messageChanged(text: String) {
        val wasBlank = _uiState.value.message.isBlank()
        _uiState.update { it.copy(message = text) }
        if (wasBlank && text.isNotBlank() && !messageStartedLogged) {
            messageStartedLogged = true
            logCategoryEvent(FeedbackEventName.MESSAGE_STARTED)
        }
    }

    private fun nextStep() {
        when (_uiState.value.step) {
            FeedbackStep.CategoryPicker -> Unit // selecting a category is what advances this step
            FeedbackStep.Form -> _uiState.update { it.copy(step = FeedbackStep.Review) }
            FeedbackStep.Review, FeedbackStep.Sent -> Unit
        }
    }

    private fun previousStep() {
        when (_uiState.value.step) {
            FeedbackStep.CategoryPicker -> Unit
            FeedbackStep.Form -> _uiState.update { it.copy(step = FeedbackStep.CategoryPicker, category = null) }
            FeedbackStep.Review -> _uiState.update { it.copy(step = FeedbackStep.Form) }
            FeedbackStep.Sent -> Unit
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.isSubmitting || !state.canSubmit) return
        val category = state.category ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, isOffline = false) }

            val diagnostics = if (state.includeDiagnostics) deviceInfoProvider.collect() else null
            val payload = UserFeedbackPayload(
                category = category,
                area = state.area,
                message = state.message.ifBlank { null },
                secondaryMessage = state.secondaryMessage.ifBlank { null },
                quickReason = state.quickReason,
                priority = state.priority,
                rating = state.rating,
                keepMessage = state.keepMessage.ifBlank { null },
                improveMessage = state.improveMessage.ifBlank { null },
                source = source,
                originScreen = originScreen,
                includeDiagnostics = state.includeDiagnostics,
                appVersion = diagnostics?.appVersion,
                androidVersion = diagnostics?.androidVersion,
                deviceModel = diagnostics?.deviceModel,
                connectionType = diagnostics?.connectionType,
                clientFeedbackId = state.clientFeedbackId,
            )

            when (val result = feedbackRepository.submitUserFeedback(payload)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, step = FeedbackStep.Sent) }
                    logCategoryEvent(FeedbackEventName.SENT)
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            isOffline = result.isNetworkError,
                            errorMessage = if (result.isNetworkError) OFFLINE_MESSAGE else result.message,
                        )
                    }
                    logCategoryEvent(FeedbackEventName.SEND_FAILED)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val state = _uiState.value
        if (state.category != null && state.step != FeedbackStep.Sent) {
            logCategoryEvent(FeedbackEventName.ABANDONED)
        }
    }
}
