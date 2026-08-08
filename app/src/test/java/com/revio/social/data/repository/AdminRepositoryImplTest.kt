package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.ModerationReason
import com.revio.social.data.remote.api.AdminApi
import com.revio.social.data.remote.dto.admin.AdminUserSummaryDto
import com.revio.social.data.remote.dto.admin.BanStateDto
import com.revio.social.data.remote.dto.admin.BanUserRequestDto
import com.revio.social.data.remote.dto.admin.ModerationDecision
import com.revio.social.data.remote.dto.admin.RemovePostRequestDto
import com.revio.social.data.remote.dto.admin.RemovePostResponseDto
import com.revio.social.data.remote.dto.admin.ReportAdminDto
import com.revio.social.data.remote.dto.admin.ReportStatus
import com.revio.social.data.remote.dto.admin.ResolveReportRequestDto
import com.revio.social.data.model.ReportReason
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Instant
import java.util.UUID

/**
 * AdminRepositoryImpl is thin: builds the right request DTO and passes it through safeApiCall.
 * We test exactly that — the correct request shape goes out, and both Success/Error pass through
 * unchanged — mirroring AuthRepositoryImplTest's own scope and style.
 */
class AdminRepositoryImplTest {

    private lateinit var adminApi: AdminApi
    private lateinit var repo: AdminRepositoryImpl

    @Before
    fun setup() {
        adminApi = mockk()
        repo = AdminRepositoryImpl(adminApi)
    }

    private fun errorBody(message: String) =
        """{"error":"$message"}""".toResponseBody("application/json".toMediaType())

    @Test
    fun `listReports passes the status name as a query param`() = runTest {
        coEvery { adminApi.listReports("PENDING") } returns Response.success(emptyList())

        val result = repo.listReports(ReportStatus.PENDING)

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { adminApi.listReports("PENDING") }
    }

    @Test
    fun `listReports with null status passes a null query param`() = runTest {
        coEvery { adminApi.listReports(null) } returns Response.success(emptyList())

        repo.listReports(null)

        coVerify(exactly = 1) { adminApi.listReports(null) }
    }

    @Test
    fun `listReports error is passed through as ApiResult Error`() = runTest {
        coEvery { adminApi.listReports(any()) } returns Response.error(500, errorBody("Server error"))

        val result = repo.listReports(ReportStatus.PENDING)

        assertTrue(result is ApiResult.Error)
        assertEquals("Server error", (result as ApiResult.Error).message)
    }

    @Test
    fun `resolveReport wraps the decision in ResolveReportRequestDto`() = runTest {
        val id = UUID.randomUUID()
        val captured = slot<ResolveReportRequestDto>()
        val response = ReportAdminDto(
            id = id,
            reporterId = UUID.randomUUID(),
            postId = UUID.randomUUID(),
            reason = ReportReason.DUPLICATE_POST,
            status = ReportStatus.REVIEWED,
            createdAt = Instant.EPOCH,
        )
        coEvery { adminApi.resolveReport(id, capture(captured)) } returns Response.success(response)

        val result = repo.resolveReport(id, ModerationDecision.UPHOLD)

        assertEquals(ModerationDecision.UPHOLD, captured.captured.decision)
        assertEquals(ApiResult.Success(response), result)
    }

    @Test
    fun `removePost wraps reason and reasonDetails in RemovePostRequestDto`() = runTest {
        val postId = UUID.randomUUID()
        val captured = slot<RemovePostRequestDto>()
        val response = RemovePostResponseDto(violationId = UUID.randomUUID(), postId = postId)
        coEvery { adminApi.removePost(postId, capture(captured)) } returns Response.success(response)

        val result = repo.removePost(postId, ModerationReason.OTHER, "custom text")

        assertEquals(ModerationReason.OTHER, captured.captured.reason)
        assertEquals("custom text", captured.captured.reasonDetails)
        assertEquals(ApiResult.Success(response), result)
    }

    @Test
    fun `removePost for an unknown post surfaces the 404 as ApiResult Error`() = runTest {
        val postId = UUID.randomUUID()
        coEvery { adminApi.removePost(eq(postId), any()) } returns Response.error(404, errorBody("Post not found"))

        val result = repo.removePost(postId, ModerationReason.SPAM_OR_MISLEADING, null)

        assertTrue(result is ApiResult.Error)
        assertEquals("Post not found", (result as ApiResult.Error).message)
    }

    @Test
    fun `searchUsers passes the query through untouched`() = runTest {
        val users = listOf(
            AdminUserSummaryDto(
                id = UUID.randomUUID(),
                username = "alice",
                email = "alice@example.com",
                fullName = "Alice",
                banState = BanStateDto(isBanned = false, permanent = false),
            )
        )
        coEvery { adminApi.searchUsers("alice") } returns Response.success(users)

        val result = repo.searchUsers("alice")

        assertEquals(ApiResult.Success(users), result)
    }

    @Test
    fun `banUser wraps duration, permanent and reason in BanUserRequestDto`() = runTest {
        val userId = UUID.randomUUID()
        val captured = slot<BanUserRequestDto>()
        coEvery { adminApi.banUser(userId, capture(captured)) } returns Response.success(Unit)

        val result = repo.banUser(userId, durationDays = 7, permanent = false, reason = "spam")

        assertEquals(7, captured.captured.durationDays)
        assertEquals(false, captured.captured.permanent)
        assertEquals("spam", captured.captured.reason)
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `banUser for an unknown user surfaces the 404 as ApiResult Error`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { adminApi.banUser(eq(userId), any()) } returns Response.error(404, errorBody("User not found"))

        val result = repo.banUser(userId, null, true, null)

        assertTrue(result is ApiResult.Error)
        assertEquals("User not found", (result as ApiResult.Error).message)
    }

    @Test
    fun `unbanUser calls the api with the given userId`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { adminApi.unbanUser(userId) } returns Response.success(Unit)

        val result = repo.unbanUser(userId)

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { adminApi.unbanUser(userId) }
    }

    @Test
    fun `revokeViolation calls the api with the given id`() = runTest {
        val violationId = UUID.randomUUID()
        coEvery { adminApi.revokeViolation(violationId) } returns Response.success(Unit)

        val result = repo.revokeViolation(violationId)

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { adminApi.revokeViolation(violationId) }
    }
}
