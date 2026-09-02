package com.revio.social.features.notifications

import com.revio.social.MainDispatcherRule
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.data.remote.dto.notification.NotificationCategory
import com.revio.social.data.remote.dto.notification.NotificationCursorDto
import com.revio.social.data.remote.dto.notification.NotificationDto
import com.revio.social.data.remote.dto.notification.NotificationListResponseDto
import com.revio.social.data.remote.dto.notification.NotificationType
import com.revio.social.data.repository.NotificationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class NoticesViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val repository: NotificationRepository = mockk()
    private val networkAvailable = MutableStateFlow(true)
    private val connectivity: NetworkConnectivityManager = mockk {
        every { isNetworkAvailable } returns networkAvailable
    }

    private fun notification(
        id: UUID = UUID.randomUUID(),
        readAt: Instant? = null,
        category: NotificationCategory = NotificationCategory.ACCOUNT,
    ) = NotificationDto(
        id = id,
        type = NotificationType.POST_REMOVED,
        title = "Post removed",
        body = "Your post was removed.",
        blocking = false,
        createdAt = Instant.EPOCH,
        readAt = readAt,
        category = category,
    )

    @Test
    fun `init requests the ACCOUNT category and populates items`() = runTest {
        val n1 = notification()
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 1, items = listOf(n1)))

        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(n1), state.items)
        assertEquals(false, state.isLoading)
        assertNull(state.errorMessage)
        coVerify(exactly = 1) { repository.getNotifications(category = NotificationCategory.ACCOUNT) }
    }

    @Test
    fun `a defensive LIKES item in the response is not shown`() = runTest {
        val accountItem = notification(category = NotificationCategory.ACCOUNT)
        val likeItem = notification(category = NotificationCategory.LIKES)
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 1, items = listOf(accountItem, likeItem)))

        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        assertEquals(listOf(accountItem), vm.uiState.value.items)
    }

    @Test
    fun `load failure surfaces errorMessage and isOffline for a network error`() = runTest {
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Error("Network error", code = "network_unavailable")

        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Network error", state.errorMessage)
        assertTrue(state.isOffline)
    }

    @Test
    fun `markRead on an unread notification stamps readAt`() = runTest {
        val id = UUID.randomUUID()
        val n1 = notification(id = id)
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 1, items = listOf(n1)))
        coEvery { repository.markRead(id) } returns ApiResult.Success(Unit)

        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.markRead(id)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.items.single().readAt != null)
        coVerify(exactly = 1) { repository.markRead(id) }
    }

    @Test
    fun `markRead on an already-read notification does not call the repository`() = runTest {
        val id = UUID.randomUUID()
        val n1 = notification(id = id, readAt = Instant.EPOCH)
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 0, items = listOf(n1)))

        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.markRead(id)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.markRead(any()) }
    }

    @Test
    fun `onNotificationClicked marks the notification read`() = runTest {
        val id = UUID.randomUUID()
        val n1 = notification(id = id)
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 1, items = listOf(n1)))
        coEvery { repository.markRead(id) } returns ApiResult.Success(Unit)

        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.onNotificationClicked(n1)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.markRead(id) }
    }

    @Test
    fun `markRead failure sets actionErrorMessage and logs an analytics failure event (pas 5_1)`() = runTest {
        val id = UUID.randomUUID()
        val n1 = notification(id = id)
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 1, items = listOf(n1)))
        coEvery { repository.markRead(id) } returns ApiResult.Error("boom", code = "http_5xx")
        val analytics: AnalyticsClient = mockk(relaxed = true)
        val eventSlot = slot<AnalyticsEvent>()

        val vm = NoticesViewModel(repository, connectivity, analytics)
        advanceUntilIdle()

        vm.markRead(id)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Couldn't mark as read", state.actionErrorMessage)
        assertEquals(null, state.items.single().readAt) // unchanged — the optimistic update never ran
        verify(exactly = 1) { analytics.log(capture(eventSlot)) }
        assertEquals("notification_action_result", eventSlot.captured.name)

        vm.clearActionError()
        assertNull(vm.uiState.value.actionErrorMessage)
    }

    @Test
    fun `reconnecting after a network error reloads automatically`() = runTest {
        networkAvailable.value = false
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returnsMany listOf(
            ApiResult.Error("Network error", code = "network_unavailable"),
            ApiResult.Success(NotificationListResponseDto(unreadCount = 0, items = emptyList())),
        )
        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        networkAvailable.value = true
        advanceUntilIdle()

        assertNull(vm.uiState.value.errorMessage)
        coVerify(exactly = 2) { repository.getNotifications(category = NotificationCategory.ACCOUNT) }
    }

    @Test
    fun `refresh sets isRefreshing then clears it on success`() = runTest {
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 0, items = emptyList()))

        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isRefreshing)
        coVerify(exactly = 2) { repository.getNotifications(category = NotificationCategory.ACCOUNT) }
    }

    @Test
    fun `loadMore sends the cursor from the first page and appends its items`() = runTest {
        val n1 = notification()
        val cursor = NotificationCursorDto(lastCreatedAt = Instant.parse("2026-08-25T10:00:00Z"), lastNotificationId = n1.id)
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 1, items = listOf(n1), nextCursor = cursor, hasMore = true))
        val n2 = notification()
        coEvery {
            repository.getNotifications(
                category = NotificationCategory.ACCOUNT,
                cursorCreatedAt = cursor.lastCreatedAt.toString(),
                cursorNotificationId = cursor.lastNotificationId.toString(),
            )
        } returns ApiResult.Success(NotificationListResponseDto(unreadCount = 0, items = listOf(n2)))

        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(n1, n2), vm.uiState.value.items)
        assertEquals(false, vm.uiState.value.isLoadingMore)
        coVerify(exactly = 1) {
            repository.getNotifications(
                category = NotificationCategory.ACCOUNT,
                cursorCreatedAt = cursor.lastCreatedAt.toString(),
                cursorNotificationId = cursor.lastNotificationId.toString(),
            )
        }
    }

    @Test
    fun `loadMore does not relaunch while a page is already in flight`() = runTest {
        val n1 = notification()
        val cursor = NotificationCursorDto(lastCreatedAt = Instant.parse("2026-08-25T10:00:00Z"), lastNotificationId = n1.id)
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 1, items = listOf(n1), nextCursor = cursor, hasMore = true))
        // Gate the second page's response so both loadMore() calls below race while it's still in flight.
        val gate = kotlinx.coroutines.CompletableDeferred<ApiResult<NotificationListResponseDto>>()
        coEvery {
            repository.getNotifications(
                category = NotificationCategory.ACCOUNT,
                cursorCreatedAt = cursor.lastCreatedAt.toString(),
                cursorNotificationId = cursor.lastNotificationId.toString(),
            )
        } coAnswers { gate.await() }

        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.loadMore()
        vm.loadMore() // must be a no-op: isLoadingMore is already true
        advanceUntilIdle()
        gate.complete(ApiResult.Success(NotificationListResponseDto(unreadCount = 0, items = listOf(notification()))))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.getNotifications(
                category = NotificationCategory.ACCOUNT,
                cursorCreatedAt = cursor.lastCreatedAt.toString(),
                cursorNotificationId = cursor.lastNotificationId.toString(),
            )
        }
    }

    @Test
    fun `loadMore is a no-op when hasMore is false`() = runTest {
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 0, items = listOf(notification()), hasMore = false))

        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getNotifications(category = NotificationCategory.ACCOUNT) }
    }

    @Test
    fun `a loadMore failure does not clear the already-visible list`() = runTest {
        val n1 = notification()
        val cursor = NotificationCursorDto(lastCreatedAt = Instant.parse("2026-08-25T10:00:00Z"), lastNotificationId = n1.id)
        coEvery { repository.getNotifications(category = NotificationCategory.ACCOUNT) } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 1, items = listOf(n1), nextCursor = cursor, hasMore = true))
        coEvery {
            repository.getNotifications(
                category = NotificationCategory.ACCOUNT,
                cursorCreatedAt = cursor.lastCreatedAt.toString(),
                cursorNotificationId = cursor.lastNotificationId.toString(),
            )
        } returns ApiResult.Error("boom", code = "http_5xx")

        val vm = NoticesViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(n1), vm.uiState.value.items)
        assertEquals(false, vm.uiState.value.isLoadingMore)
    }
}
