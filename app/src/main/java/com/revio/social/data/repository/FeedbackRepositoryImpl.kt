package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ErrorPolicy
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.core.network.isNetworkError
import com.revio.social.core.network.map
import com.revio.social.core.network.onValidatedReconnect
import com.revio.social.core.network.safeApiCall
import com.revio.social.core.network.safeApiCallNoContent
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.model.FIRST_POST_FEEDBACK_KEY
import com.revio.social.data.model.FeedbackPromptState
import com.revio.social.data.model.FirstPostFeedbackPayload
import com.revio.social.data.model.PromptEvent
import com.revio.social.data.model.UserFeedbackPayload
import com.revio.social.data.remote.api.FeedbackApi
import com.revio.social.data.remote.dto.feedback.PromptStateUpdateRequest
import com.revio.social.data.remote.dto.feedback.SubmitFirstPostFeedbackRequest
import com.revio.social.data.remote.dto.feedback.SubmitUserFeedbackRequest
import com.revio.social.data.remote.dto.feedback.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface FeedbackRepository {
    suspend fun getPromptState(): ApiResult<FeedbackPromptState>
    suspend fun submit(payload: FirstPostFeedbackPayload): ApiResult<Unit>
    suspend fun reportShown(): ApiResult<Unit>
    suspend fun reportDismissed(): ApiResult<Unit>
    suspend fun submitUserFeedback(payload: UserFeedbackPayload): ApiResult<Unit>
}

@Singleton
class FeedbackRepositoryImpl @Inject constructor(
    private val feedbackApi: FeedbackApi,
    private val userPreferences: UserPreferences,
    networkConnectivityManager: NetworkConnectivityManager,
) : FeedbackRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // A submit that failed while offline is queued locally (see [submit]); retry it once
        // connectivity is back so the user's feedback isn't silently lost.
        scope.launch {
            networkConnectivityManager.onValidatedReconnect().collect {
                retryPendingSubmission()
                retryPendingUserFeedbackSubmission()
            }
        }
    }

    override suspend fun getPromptState(): ApiResult<FeedbackPromptState> {
        return safeApiCall(policy = ErrorPolicy.SILENT) { feedbackApi.getPromptState(FIRST_POST_FEEDBACK_KEY) }
            .map { it.toDomain() }
    }

    override suspend fun submit(payload: FirstPostFeedbackPayload): ApiResult<Unit> {
        val request = SubmitFirstPostFeedbackRequest(
            rating = payload.rating,
            quickReason = payload.quickReason,
            comment = payload.comment,
            surface = payload.surface,
            appVersion = payload.appVersion,
            androidVersion = payload.androidVersion,
            deviceModel = payload.deviceModel,
            connectionType = payload.connectionType,
            uploadDurationMs = payload.uploadDurationMs,
            hadRetries = payload.hadRetries,
            lastErrorCode = payload.lastErrorCode,
            clientSubmittedAt = payload.clientSubmittedAt,
        )
        val result = safeApiCallNoContent { feedbackApi.submitFirstPostFeedback(request) }

        if (result is ApiResult.Error && result.isNetworkError) {
            // Offline: keep the response so it isn't lost — retried automatically on reconnect.
            userPreferences.userId.first()?.let { userId ->
                userPreferences.setPendingFirstPostFeedback(userId, request)
            }
        }

        return result
    }

    override suspend fun reportShown(): ApiResult<Unit> {
        return safeApiCallNoContent(policy = ErrorPolicy.SILENT) {
            feedbackApi.updatePromptState(PromptStateUpdateRequest(FIRST_POST_FEEDBACK_KEY, PromptEvent.SHOWN))
        }
    }

    override suspend fun reportDismissed(): ApiResult<Unit> {
        return safeApiCallNoContent(policy = ErrorPolicy.SILENT) {
            feedbackApi.updatePromptState(PromptStateUpdateRequest(FIRST_POST_FEEDBACK_KEY, PromptEvent.DISMISSED))
        }
    }

    private suspend fun retryPendingSubmission() {
        val userId = userPreferences.userId.first() ?: return
        val pending = userPreferences.pendingFirstPostFeedback(userId).first() ?: return

        val result = safeApiCallNoContent { feedbackApi.submitFirstPostFeedback(pending) }
        if (result is ApiResult.Success) {
            userPreferences.setPendingFirstPostFeedback(userId, null)
        }
    }

    override suspend fun submitUserFeedback(payload: UserFeedbackPayload): ApiResult<Unit> {
        val request = SubmitUserFeedbackRequest(
            category = payload.category,
            area = payload.area,
            message = payload.message,
            secondaryMessage = payload.secondaryMessage,
            quickReason = payload.quickReason,
            priority = payload.priority,
            rating = payload.rating,
            keepMessage = payload.keepMessage,
            improveMessage = payload.improveMessage,
            source = payload.source,
            originScreen = payload.originScreen,
            includeDiagnostics = payload.includeDiagnostics,
            appVersion = payload.appVersion,
            androidVersion = payload.androidVersion,
            deviceModel = payload.deviceModel,
            connectionType = payload.connectionType,
            lastErrorCode = payload.lastErrorCode,
            clientFeedbackId = payload.clientFeedbackId,
            clientSubmittedAt = payload.clientSubmittedAt,
        )
        val result = safeApiCallNoContent { feedbackApi.submitUserFeedback(request) }

        if (result is ApiResult.Error && result.isNetworkError) {
            // Offline: keep the response so it isn't lost — retried automatically on reconnect.
            // clientFeedbackId is generated once per form, not per attempt, so the retry stays
            // idempotent with the server's unique constraint on it.
            userPreferences.userId.first()?.let { userId ->
                userPreferences.setPendingUserFeedback(userId, request)
            }
        }

        return result
    }

    private suspend fun retryPendingUserFeedbackSubmission() {
        val userId = userPreferences.userId.first() ?: return
        val pending = userPreferences.pendingUserFeedback(userId).first() ?: return

        val result = safeApiCallNoContent { feedbackApi.submitUserFeedback(pending) }
        if (result is ApiResult.Success) {
            userPreferences.setPendingUserFeedback(userId, null)
        }
    }
}
