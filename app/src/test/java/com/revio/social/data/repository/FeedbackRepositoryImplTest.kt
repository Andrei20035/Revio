package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ErrorPolicy
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.core.network.isNetworkError
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.model.FeedbackCategory
import com.revio.social.data.model.FeedbackSource
import com.revio.social.data.model.FirstPostFeedbackPayload
import com.revio.social.data.model.UserFeedbackPayload
import com.revio.social.data.remote.api.FeedbackApi
import com.revio.social.data.remote.dto.feedback.SubmitFirstPostFeedbackRequest
import com.revio.social.data.remote.dto.feedback.SubmitUserFeedbackRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.UUID

/**
 * Covers the offline-resilience behavior added on top of the plain submit/report calls: a
 * submit that fails while offline is queued locally (never lost) and retried automatically once
 * a validated reconnect is observed — mirroring the `onValidatedReconnect()` idiom already used
 * by `FeedViewModel`/`LeaderboardViewModel`.
 */
class FeedbackRepositoryImplTest {

    private val userId: UUID = UUID.randomUUID()

    private fun payload(rating: Int = 5) = FirstPostFeedbackPayload(rating = rating)

    private fun userFeedbackPayload(
        category: FeedbackCategory = FeedbackCategory.GENERAL,
        message: String? = "hello",
        rating: Int? = 4,
        clientFeedbackId: UUID = UUID.randomUUID(),
    ) = UserFeedbackPayload(
        category = category,
        message = message,
        rating = rating,
        source = FeedbackSource.SETTINGS_FEEDBACK,
        clientFeedbackId = clientFeedbackId,
    )

    private fun repository(
        feedbackApi: FeedbackApi,
        pending: SubmitFirstPostFeedbackRequest? = null,
        pendingUserFeedback: SubmitUserFeedbackRequest? = null,
        internetValidated: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ): Triple<FeedbackRepositoryImpl, UserPreferences, MutableStateFlow<Boolean>> {
        var storedPending = pending
        var storedPendingUserFeedback = pendingUserFeedback
        val userPreferences: UserPreferences = mockk()
        every { userPreferences.userId } returns flowOf(userId)
        every { userPreferences.pendingFirstPostFeedback(userId) } answers { flowOf(storedPending) }
        coEvery { userPreferences.setPendingFirstPostFeedback(userId, any()) } answers {
            storedPending = secondArg()
        }
        every { userPreferences.pendingUserFeedback(userId) } answers { flowOf(storedPendingUserFeedback) }
        coEvery { userPreferences.setPendingUserFeedback(userId, any()) } answers {
            storedPendingUserFeedback = secondArg()
        }

        val connectivity: NetworkConnectivityManager = mockk()
        every { connectivity.isInternetValidated } returns internetValidated

        val repo = FeedbackRepositoryImpl(feedbackApi, userPreferences, connectivity)
        return Triple(repo, userPreferences, internetValidated)
    }

    @Test
    fun `submit persists the payload as pending on a network error`() = runTest {
        val feedbackApi: FeedbackApi = mockk()
        coEvery { feedbackApi.submitFirstPostFeedback(any()) } throws IOException()
        val (repo, userPreferences, _) = repository(feedbackApi)

        val result = repo.submit(payload())

        assertTrue(result is ApiResult.Error)
        coVerify(exactly = 1) { userPreferences.setPendingFirstPostFeedback(userId, any()) }
    }

    @Test
    fun `submit does not persist pending on a non-network error`() = runTest {
        val feedbackApi: FeedbackApi = mockk()
        val errorBody = """{"error":"Invalid rating"}""".toResponseBody("application/json".toMediaType())
        coEvery { feedbackApi.submitFirstPostFeedback(any()) } returns Response.error(400, errorBody)
        val (repo, userPreferences, _) = repository(feedbackApi)

        val result = repo.submit(payload())

        assertTrue(result is ApiResult.Error)
        coVerify(exactly = 0) { userPreferences.setPendingFirstPostFeedback(any(), any()) }
    }

    @Test
    fun `submit does not persist pending on success`() = runTest {
        val feedbackApi: FeedbackApi = mockk()
        coEvery { feedbackApi.submitFirstPostFeedback(any()) } returns Response.success(Unit)
        val (repo, userPreferences, _) = repository(feedbackApi)

        val result = repo.submit(payload())

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 0) { userPreferences.setPendingFirstPostFeedback(any(), any()) }
    }

    @Test
    fun `a validated reconnect retries a pending submission and clears it on success`() {
        val pending = SubmitFirstPostFeedbackRequest(rating = 4)
        val feedbackApi: FeedbackApi = mockk()
        coEvery { feedbackApi.submitFirstPostFeedback(pending) } returns Response.success(Unit)
        val (_, userPreferences, internetValidated) = repository(feedbackApi, pending = pending)

        Thread.sleep(100) // let the repo's background collector subscribe before flipping
        internetValidated.value = true
        Thread.sleep(800) // past the 400ms reconnect debounce

        coVerify(exactly = 1) { feedbackApi.submitFirstPostFeedback(pending) }
        coVerify(exactly = 1) { userPreferences.setPendingFirstPostFeedback(userId, null) }
    }

    @Test
    fun `a validated reconnect does nothing when there is no pending submission`() {
        val feedbackApi: FeedbackApi = mockk()
        val (_, _, internetValidated) = repository(feedbackApi, pending = null)

        Thread.sleep(100)
        internetValidated.value = true
        Thread.sleep(800)

        coVerify(exactly = 0) { feedbackApi.submitFirstPostFeedback(any()) }
    }

    @Test
    fun `a failed retry keeps the pending submission`() {
        val pending = SubmitFirstPostFeedbackRequest(rating = 2)
        val feedbackApi: FeedbackApi = mockk()
        coEvery { feedbackApi.submitFirstPostFeedback(pending) } throws IOException()
        val (_, userPreferences, internetValidated) = repository(feedbackApi, pending = pending)

        Thread.sleep(100)
        internetValidated.value = true
        Thread.sleep(800)

        coVerify(exactly = 1) { feedbackApi.submitFirstPostFeedback(pending) }
        coVerify(exactly = 0) { userPreferences.setPendingFirstPostFeedback(userId, null) }
    }

    @Test
    fun `submitUserFeedback returns Success and does not persist pending on success`() = runTest {
        val feedbackApi: FeedbackApi = mockk()
        coEvery { feedbackApi.submitUserFeedback(any()) } returns Response.success(Unit)
        val (repo, userPreferences, _) = repository(feedbackApi)

        val result = repo.submitUserFeedback(userFeedbackPayload())

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 0) { userPreferences.setPendingUserFeedback(any(), any()) }
    }

    @Test
    fun `submitUserFeedback persists the payload as pending on a network error`() = runTest {
        val feedbackApi: FeedbackApi = mockk()
        coEvery { feedbackApi.submitUserFeedback(any()) } throws IOException()
        val (repo, userPreferences, _) = repository(feedbackApi)

        val result = repo.submitUserFeedback(userFeedbackPayload())

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).isNetworkError)
        coVerify(exactly = 1) { userPreferences.setPendingUserFeedback(userId, any()) }
    }

    @Test
    fun `submitUserFeedback does not persist pending on a non-network error`() = runTest {
        val feedbackApi: FeedbackApi = mockk()
        val errorBody = """{"error":"Invalid category"}""".toResponseBody("application/json".toMediaType())
        coEvery { feedbackApi.submitUserFeedback(any()) } returns Response.error(400, errorBody)
        val (repo, userPreferences, _) = repository(feedbackApi)

        val result = repo.submitUserFeedback(userFeedbackPayload())

        assertTrue(result is ApiResult.Error)
        coVerify(exactly = 0) { userPreferences.setPendingUserFeedback(any(), any()) }
    }

    @Test
    fun `a validated reconnect retries a pending user feedback submission with the same clientFeedbackId and clears it on success`() {
        val clientFeedbackId = UUID.randomUUID()
        val pending = SubmitUserFeedbackRequest(
            category = com.revio.social.data.model.FeedbackCategory.GENERAL,
            message = "hello",
            rating = 4,
            source = com.revio.social.data.model.FeedbackSource.SETTINGS_FEEDBACK,
            clientFeedbackId = clientFeedbackId,
        )
        val feedbackApi: FeedbackApi = mockk()
        coEvery { feedbackApi.submitUserFeedback(pending) } returns Response.success(Unit)
        val (_, userPreferences, internetValidated) = repository(feedbackApi, pendingUserFeedback = pending)

        Thread.sleep(100) // let the repo's background collector subscribe before flipping
        internetValidated.value = true
        Thread.sleep(800) // past the 400ms reconnect debounce

        coVerify(exactly = 1) { feedbackApi.submitUserFeedback(pending) }
        coVerify(exactly = 1) { userPreferences.setPendingUserFeedback(userId, null) }
    }

    @Test
    fun `a validated reconnect still retries the pending first-post submission alongside user feedback`() {
        val firstPostPending = SubmitFirstPostFeedbackRequest(rating = 4)
        val feedbackApi: FeedbackApi = mockk()
        coEvery { feedbackApi.submitFirstPostFeedback(firstPostPending) } returns Response.success(Unit)
        val (_, userPreferences, internetValidated) =
            repository(feedbackApi, pending = firstPostPending, pendingUserFeedback = null)

        Thread.sleep(100)
        internetValidated.value = true
        Thread.sleep(800)

        coVerify(exactly = 1) { feedbackApi.submitFirstPostFeedback(firstPostPending) }
        coVerify(exactly = 1) { userPreferences.setPendingFirstPostFeedback(userId, null) }
    }

    // ── Pas 1.7c — best-effort call-sites tagged SILENT, so a later step never reports them ──

    @Test
    fun `getPromptState is tagged SILENT on failure`() = runTest {
        val feedbackApi: FeedbackApi = mockk()
        coEvery { feedbackApi.getPromptState(any()) } throws RuntimeException("boom")
        val (repo, _, _) = repository(feedbackApi)

        val result = repo.getPromptState()

        assertEquals(ErrorPolicy.SILENT, (result as ApiResult.Error).policy)
    }

    @Test
    fun `reportShown is tagged SILENT on failure`() = runTest {
        val feedbackApi: FeedbackApi = mockk()
        coEvery { feedbackApi.updatePromptState(any()) } throws RuntimeException("boom")
        val (repo, _, _) = repository(feedbackApi)

        val result = repo.reportShown()

        assertEquals(ErrorPolicy.SILENT, (result as ApiResult.Error).policy)
    }

    @Test
    fun `reportDismissed is tagged SILENT on failure`() = runTest {
        val feedbackApi: FeedbackApi = mockk()
        coEvery { feedbackApi.updatePromptState(any()) } throws RuntimeException("boom")
        val (repo, _, _) = repository(feedbackApi)

        val result = repo.reportDismissed()

        assertEquals(ErrorPolicy.SILENT, (result as ApiResult.Error).policy)
    }
}
