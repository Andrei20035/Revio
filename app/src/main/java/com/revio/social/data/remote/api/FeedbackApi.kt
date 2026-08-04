package com.revio.social.data.remote.api

import com.revio.social.data.remote.dto.feedback.FeedbackPromptStateDto
import com.revio.social.data.remote.dto.feedback.PromptStateUpdateRequest
import com.revio.social.data.remote.dto.feedback.SubmitFirstPostFeedbackRequest
import com.revio.social.data.remote.dto.feedback.SubmitUserFeedbackRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface FeedbackApi {

    /**
     * Submits first-post feedback. Returns 201 on a new submission and 200 when the user
     * had already submitted — both are treated as success by the repo.
     */
    @POST("feedback/first-post")
    suspend fun submitFirstPostFeedback(
        @Body request: SubmitFirstPostFeedbackRequest,
    ): Response<Unit>

    @GET("feedback/prompt-state")
    suspend fun getPromptState(
        @Query("key") key: String,
    ): Response<FeedbackPromptStateDto>

    @POST("feedback/prompt-state")
    suspend fun updatePromptState(
        @Body request: PromptStateUpdateRequest,
    ): Response<Unit>

    /**
     * Submits Settings feedback (bug report, confusion, feature idea, or general feedback).
     * Idempotent on `clientFeedbackId`: returns 201 on a new submission and 200 when the same
     * id was already submitted — both are treated as success by the repo.
     */
    @POST("feedback/user")
    suspend fun submitUserFeedback(
        @Body request: SubmitUserFeedbackRequest,
    ): Response<Unit>
}
