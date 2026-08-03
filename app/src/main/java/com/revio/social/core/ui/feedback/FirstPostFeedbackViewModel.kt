package com.revio.social.core.ui.feedback

import androidx.lifecycle.ViewModel
import com.revio.social.core.feedback.FeedbackEventName
import com.revio.social.core.feedback.FirstPostFeedbackController
import com.revio.social.core.feedback.FirstPostPromptState
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.FeedbackSurface
import com.revio.social.data.model.FirstPostFeedbackPayload
import com.revio.social.data.model.QuickReason
import com.revio.social.data.repository.FeedbackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val CONFIRMATION_MESSAGE = "Thanks — your feedback helps us improve Revio."
private const val SUBMIT_ERROR_MESSAGE = "Couldn't send feedback. Try again."

/**
 * Owns the rating → reason → comment progression and submission for the first-post feedback
 * card. Singleton (not screen-scoped) so Feed's and Profile's independent
 * [FirstPostFeedbackViewModel] instances — each tied to its own NavBackStackEntry — observe the
 * exact same in-flight card state. Without this, navigating away mid-flow (e.g. tapping a tab
 * while picking a reason) would silently reset the card back to the Rating step on the other
 * screen instead of preserving progress.
 */
@Singleton
class FirstPostFeedbackCardCoordinator @Inject constructor(
    private val controller: FirstPostFeedbackController,
    private val feedbackRepository: FeedbackRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _cardState = MutableStateFlow<FirstPostFeedbackCardState?>(null)
    val cardState: StateFlow<FirstPostFeedbackCardState?> = _cardState.asStateFlow()

    private val _confirmationMessage = MutableStateFlow<String?>(null)
    val confirmationMessage: StateFlow<String?> = _confirmationMessage.asStateFlow()

    val isTourActive: StateFlow<Boolean> = controller.isTourActive

    private var lastSurface: FeedbackSurface = FeedbackSurface.FEED

    init {
        scope.launch {
            controller.state.collect { promptState ->
                when (promptState) {
                    is FirstPostPromptState.Rating -> {
                        _cardState.value = FirstPostFeedbackCardState(step = FirstPostFeedbackStep.Rating)
                    }
                    is FirstPostPromptState.Hidden -> {
                        _cardState.value = null
                        _confirmationMessage.value = null
                        lastSurface = FeedbackSurface.FEED
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onSurfaceReady(surface: FeedbackSurface, isBlocked: () -> Boolean) {
        lastSurface = surface
        controller.onSurfaceReady(surface, isBlocked)
    }

    fun cancelPendingShow() = controller.cancelPendingShow()

    fun onRatingSelected(rating: Int) {
        _cardState.value = FirstPostFeedbackCardState(step = FirstPostFeedbackStep.Reason(rating))
    }

    fun onReasonSelected(reason: QuickReason) {
        val rating = (_cardState.value?.step as? FirstPostFeedbackStep.Reason)?.rating ?: return
        _cardState.value = FirstPostFeedbackCardState(step = FirstPostFeedbackStep.Comment(rating, reason))
    }

    fun onCommentChanged(text: String) {
        _cardState.value = _cardState.value?.copy(comment = text)
    }

    fun onSend() = submit(includeComment = true)

    fun onSkip() = submit(includeComment = false)

    private fun buildPayload(rating: Int, quickReason: QuickReason?, comment: String?) = FirstPostFeedbackPayload(
        rating = rating,
        quickReason = quickReason,
        comment = comment,
        surface = lastSurface,
    )

    private fun submit(includeComment: Boolean) {
        val current = _cardState.value ?: return
        val step = current.step as? FirstPostFeedbackStep.Comment ?: return

        scope.launch {
            _cardState.value = current.copy(isSubmitting = true, errorMessage = null)

            val payload = buildPayload(
                rating = step.rating,
                quickReason = step.reason,
                comment = if (includeComment) current.comment.trim().ifBlank { null } else null,
            )

            when (feedbackRepository.submit(payload)) {
                is ApiResult.Success -> {
                    _cardState.value = null
                    _confirmationMessage.value = CONFIRMATION_MESSAGE
                    controller.onSubmitted()
                }
                is ApiResult.Error -> {
                    _cardState.value = current.copy(isSubmitting = false, errorMessage = SUBMIT_ERROR_MESSAGE)
                }
            }
        }
    }

    /**
     * A rating (and, if chosen, a reason) already captured before the card was closed is valid
     * signal and shouldn't be thrown away — returns the payload to submit for whatever step the
     * card was on, or null if nothing was selected yet (the [FirstPostFeedbackStep.Rating] step).
     */
    private fun partialPayloadOrNull(): FirstPostFeedbackPayload? {
        return when (val step = _cardState.value?.step) {
            null, is FirstPostFeedbackStep.Rating -> null
            is FirstPostFeedbackStep.Reason ->
                buildPayload(rating = step.rating, quickReason = null, comment = null)
            is FirstPostFeedbackStep.Comment ->
                buildPayload(rating = step.rating, quickReason = step.reason, comment = null)
        }
    }

    fun onNotNow() = closeWithPartialSubmit(FeedbackEventName.NOT_NOW) { controller.onNotNow() }

    fun onCloseX() = closeWithPartialSubmit(FeedbackEventName.CLOSED_X) { controller.onClosedX() }

    private fun closeWithPartialSubmit(dismissEventName: FeedbackEventName, onNoSelection: () -> Unit) {
        val payload = partialPayloadOrNull()
        _cardState.value = null

        if (payload == null) {
            onNoSelection()
            return
        }

        // Fire-and-forget: a network failure is already queued and retried by
        // FeedbackRepositoryImpl.submit, so the card can close immediately either way.
        scope.launch { feedbackRepository.submit(payload) }
        controller.onSubmitted(dismissEventName)
    }

    fun consumeConfirmation() {
        _confirmationMessage.value = null
    }
}

/** Thin per-screen proxy over the singleton [FirstPostFeedbackCardCoordinator]. */
@HiltViewModel
class FirstPostFeedbackViewModel @Inject constructor(
    private val coordinator: FirstPostFeedbackCardCoordinator,
) : ViewModel() {

    val cardState: StateFlow<FirstPostFeedbackCardState?> = coordinator.cardState
    val confirmationMessage: StateFlow<String?> = coordinator.confirmationMessage
    val isTourActive: StateFlow<Boolean> = coordinator.isTourActive

    fun onSurfaceReady(surface: FeedbackSurface, isBlocked: () -> Boolean) =
        coordinator.onSurfaceReady(surface, isBlocked)

    fun cancelPendingShow() = coordinator.cancelPendingShow()
    fun onRatingSelected(rating: Int) = coordinator.onRatingSelected(rating)
    fun onReasonSelected(reason: QuickReason) = coordinator.onReasonSelected(reason)
    fun onCommentChanged(text: String) = coordinator.onCommentChanged(text)
    fun onSend() = coordinator.onSend()
    fun onSkip() = coordinator.onSkip()
    fun onNotNow() = coordinator.onNotNow()
    fun onCloseX() = coordinator.onCloseX()
    fun consumeConfirmation() = coordinator.consumeConfirmation()
}
