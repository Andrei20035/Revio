package com.revio.social.features.admin

import com.revio.social.MainDispatcherRule
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.ModerationReason
import com.revio.social.data.remote.dto.admin.AdminUserSummaryDto
import com.revio.social.data.remote.dto.admin.BanStateDto
import com.revio.social.data.remote.dto.admin.ModerationDecision
import com.revio.social.data.remote.dto.admin.RemovePostResponseDto
import com.revio.social.data.remote.dto.admin.ReportAdminDto
import com.revio.social.data.remote.dto.admin.ReportStatus
import com.revio.social.data.remote.dto.admin.UserModerationResponseDto
import com.revio.social.data.remote.dto.notification.NotificationDto
import com.revio.social.data.repository.AdminRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val repository: AdminRepository = mockk()

    private fun report(id: UUID = UUID.randomUUID()) = ReportAdminDto(
        id = id,
        reporterId = UUID.randomUUID(),
        postId = UUID.randomUUID(),
        reason = com.revio.social.data.model.ReportReason.INAPPROPRIATE_CONTENT,
        status = ReportStatus.PENDING,
        createdAt = Instant.EPOCH,
    )

    private fun userSummary(id: UUID = UUID.randomUUID(), isBanned: Boolean = false) = AdminUserSummaryDto(
        id = id,
        username = "alice",
        email = "alice@example.com",
        fullName = "Alice",
        banState = BanStateDto(isBanned = isBanned, permanent = isBanned, bannedUntil = null, reason = null),
    )

    private fun moderationDetail(userId: UUID, isBanned: Boolean = false) = UserModerationResponseDto(
        user = userSummary(userId, isBanned),
        activeViolationCount = 0,
        needsReview = false,
        violations = emptyList(),
        recentNotifications = emptyList<NotificationDto>(),
    )

    // ---------- Reports queue ----------

    @Test
    fun `loadReports populates the reports list`() = runTest {
        val reports = listOf(report(), report())
        coEvery { repository.listReports(ReportStatus.PENDING) } returns ApiResult.Success(reports)

        val vm = AdminViewModel(repository)
        vm.loadReports()
        advanceUntilIdle()

        val state = vm.reportsState.value
        assertEquals(reports, state.reports)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadReports failure surfaces errorMessage`() = runTest {
        coEvery { repository.listReports(ReportStatus.PENDING) } returns ApiResult.Error("Server error")

        val vm = AdminViewModel(repository)
        vm.loadReports()
        advanceUntilIdle()

        assertEquals("Server error", vm.reportsState.value.errorMessage)
    }

    @Test
    fun `resolveReport removes the report from the list on success`() = runTest {
        val r1 = report()
        val r2 = report()
        coEvery { repository.listReports(ReportStatus.PENDING) } returns ApiResult.Success(listOf(r1, r2))
        coEvery { repository.resolveReport(r1.id, ModerationDecision.UPHOLD) } returns ApiResult.Success(r1)

        val vm = AdminViewModel(repository)
        vm.loadReports()
        advanceUntilIdle()

        vm.resolveReport(r1.id, ModerationDecision.UPHOLD)
        advanceUntilIdle()

        val state = vm.reportsState.value
        assertEquals(listOf(r2), state.reports)
        assertNull(state.resolvingReportId)
    }

    @Test
    fun `resolveReport failure keeps the report and surfaces an error`() = runTest {
        val r1 = report()
        coEvery { repository.listReports(ReportStatus.PENDING) } returns ApiResult.Success(listOf(r1))
        coEvery { repository.resolveReport(r1.id, ModerationDecision.DISMISS) } returns ApiResult.Error("boom")

        val vm = AdminViewModel(repository)
        vm.loadReports()
        advanceUntilIdle()

        vm.resolveReport(r1.id, ModerationDecision.DISMISS)
        advanceUntilIdle()

        val state = vm.reportsState.value
        assertEquals(listOf(r1), state.reports)
        assertEquals("boom", state.errorMessage)
    }

    // ---------- User search / moderation ----------

    @Test
    fun `searchUsers with a blank query does not call the repository`() = runTest {
        val vm = AdminViewModel(repository)
        vm.onQueryChanged("   ")
        vm.searchUsers()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.searchUsers(any()) }
    }

    @Test
    fun `searchUsers populates searchResults`() = runTest {
        val results = listOf(userSummary())
        coEvery { repository.searchUsers("alice") } returns ApiResult.Success(results)

        val vm = AdminViewModel(repository)
        vm.onQueryChanged("alice")
        vm.searchUsers()
        advanceUntilIdle()

        assertEquals(results, vm.userState.value.searchResults)
    }

    @Test
    fun `selectUser loads moderation detail and isShowingDetail becomes true`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { repository.getUserModeration(userId) } returns ApiResult.Success(moderationDetail(userId))

        val vm = AdminViewModel(repository)
        vm.selectUser(userId)
        advanceUntilIdle()

        val state = vm.userState.value
        assertTrue(state.isShowingDetail)
        assertEquals(userId, state.detail?.user?.id)
    }

    @Test
    fun `backToSearch clears the selected user and detail`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { repository.getUserModeration(userId) } returns ApiResult.Success(moderationDetail(userId))

        val vm = AdminViewModel(repository)
        vm.selectUser(userId)
        advanceUntilIdle()

        vm.backToSearch()

        val state = vm.userState.value
        assertFalse(state.isShowingDetail)
        assertNull(state.detail)
    }

    @Test
    fun `ban reloads the moderation detail on success`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { repository.getUserModeration(userId) } returnsMany listOf(
            ApiResult.Success(moderationDetail(userId, isBanned = false)),
            ApiResult.Success(moderationDetail(userId, isBanned = true)),
        )
        coEvery { repository.banUser(userId, 7, false, "spam") } returns ApiResult.Success(Unit)

        val vm = AdminViewModel(repository)
        vm.selectUser(userId)
        advanceUntilIdle()

        vm.ban(7, false, "spam")
        advanceUntilIdle()

        val state = vm.userState.value
        assertFalse(state.isMutating)
        assertTrue(state.detail?.user?.banState?.isBanned == true)
        coVerify(exactly = 1) { repository.banUser(userId, 7, false, "spam") }
    }

    @Test
    fun `ban failure surfaces mutationError and does not reload`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { repository.getUserModeration(userId) } returns ApiResult.Success(moderationDetail(userId))
        coEvery { repository.banUser(userId, 3, false, null) } returns ApiResult.Error("Invalid duration")

        val vm = AdminViewModel(repository)
        vm.selectUser(userId)
        advanceUntilIdle()

        vm.ban(3, false, null)
        advanceUntilIdle()

        assertEquals("Invalid duration", vm.userState.value.mutationError)
        coVerify(exactly = 1) { repository.getUserModeration(userId) }
    }

    @Test
    fun `unban reloads the moderation detail on success`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { repository.getUserModeration(userId) } returnsMany listOf(
            ApiResult.Success(moderationDetail(userId, isBanned = true)),
            ApiResult.Success(moderationDetail(userId, isBanned = false)),
        )
        coEvery { repository.unbanUser(userId) } returns ApiResult.Success(Unit)

        val vm = AdminViewModel(repository)
        vm.selectUser(userId)
        advanceUntilIdle()

        vm.unban()
        advanceUntilIdle()

        assertFalse(vm.userState.value.detail?.user?.banState?.isBanned == true)
    }

    @Test
    fun `revokeViolation reloads the moderation detail on success`() = runTest {
        val userId = UUID.randomUUID()
        val violationId = UUID.randomUUID()
        coEvery { repository.getUserModeration(userId) } returns ApiResult.Success(moderationDetail(userId))
        coEvery { repository.revokeViolation(violationId) } returns ApiResult.Success(Unit)

        val vm = AdminViewModel(repository)
        vm.selectUser(userId)
        advanceUntilIdle()

        vm.revokeViolation(violationId)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.revokeViolation(violationId) }
        coVerify(exactly = 2) { repository.getUserModeration(userId) }
    }

    // ---------- Post removal ----------

    @Test
    fun `removePost calls onSuccess and resets state`() = runTest {
        val postId = UUID.randomUUID()
        coEvery {
            repository.removePost(postId, ModerationReason.SPAM_OR_MISLEADING, null)
        } returns ApiResult.Success(RemovePostResponseDto(violationId = UUID.randomUUID(), postId = postId))

        val vm = AdminViewModel(repository)
        var onSuccessCalled = false
        vm.removePost(postId, ModerationReason.SPAM_OR_MISLEADING, null) { onSuccessCalled = true }
        advanceUntilIdle()

        assertTrue(onSuccessCalled)
        assertFalse(vm.removePostState.value.isSubmitting)
        assertNull(vm.removePostState.value.errorMessage)
    }

    @Test
    fun `removePost failure surfaces errorMessage and does not call onSuccess`() = runTest {
        val postId = UUID.randomUUID()
        coEvery {
            repository.removePost(postId, ModerationReason.OTHER, "custom reason")
        } returns ApiResult.Error("Post not found")

        val vm = AdminViewModel(repository)
        var onSuccessCalled = false
        vm.removePost(postId, ModerationReason.OTHER, "custom reason") { onSuccessCalled = true }
        advanceUntilIdle()

        assertFalse(onSuccessCalled)
        assertEquals("Post not found", vm.removePostState.value.errorMessage)
        assertFalse(vm.removePostState.value.isSubmitting)
    }

    @Test
    fun `two consecutive removePost calls while submitting produce a single repository call`() = runTest {
        val postId = UUID.randomUUID()
        val deferred = CompletableDeferred<ApiResult<RemovePostResponseDto>>()
        coEvery {
            repository.removePost(postId, ModerationReason.SPAM_OR_MISLEADING, null)
        } coAnswers { deferred.await() }

        val vm = AdminViewModel(repository)
        vm.removePost(postId, ModerationReason.SPAM_OR_MISLEADING, null) {}
        assertTrue("isSubmitting must already be true synchronously, before the coroutine runs", vm.removePostState.value.isSubmitting)

        vm.removePost(postId, ModerationReason.SPAM_OR_MISLEADING, null) {}
        advanceUntilIdle()

        deferred.complete(ApiResult.Success(RemovePostResponseDto(violationId = UUID.randomUUID(), postId = postId)))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.removePost(postId, ModerationReason.SPAM_OR_MISLEADING, null) }
    }

    @Test
    fun `removePost failure logs admin_remove_post_result with the failure code`() = runTest {
        val postId = UUID.randomUUID()
        val error = ApiResult.Error("Post not found", code = "post_not_found")
        coEvery {
            repository.removePost(postId, ModerationReason.SPAM_OR_MISLEADING, null)
        } returns error

        val analyticsClient: AnalyticsClient = mockk(relaxed = true)
        val vm = AdminViewModel(repository, analyticsClient)
        vm.removePost(postId, ModerationReason.SPAM_OR_MISLEADING, null) {}
        advanceUntilIdle()

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "admin_remove_post_result",
                    params = mapOf(
                        "outcome" to AnalyticsParamValue.StringValue("failure"),
                        "failure_code" to AnalyticsParamValue.StringValue("post_not_found"),
                    ),
                )
            )
        }
    }

    @Test
    fun `removePost success logs admin_remove_post_result with outcome success`() = runTest {
        val postId = UUID.randomUUID()
        coEvery {
            repository.removePost(postId, ModerationReason.SPAM_OR_MISLEADING, null)
        } returns ApiResult.Success(RemovePostResponseDto(violationId = UUID.randomUUID(), postId = postId))

        val analyticsClient: AnalyticsClient = mockk(relaxed = true)
        val vm = AdminViewModel(repository, analyticsClient)
        vm.removePost(postId, ModerationReason.SPAM_OR_MISLEADING, null) {}
        advanceUntilIdle()

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "admin_remove_post_result",
                    params = mapOf("outcome" to AnalyticsParamValue.StringValue("success")),
                )
            )
        }
    }
}
