package com.revio.social.features.notifications

import com.revio.social.MainDispatcherRule
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.data.remote.dto.notification.NotificationDto
import com.revio.social.data.remote.dto.notification.NotificationListResponseDto
import com.revio.social.data.remote.dto.notification.NotificationType
import com.revio.social.data.repository.NotificationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
class NotificationsViewModelTest {

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
    ) = NotificationDto(
        id = id,
        type = NotificationType.POST_REMOVED,
        title = "Post removed",
        body = "Your post was removed.",
        blocking = false,
        createdAt = Instant.EPOCH,
        readAt = readAt,
    )

    @Test
    fun `init loads notifications and populates unreadCount`() = runTest {
        val n1 = notification()
        coEvery { repository.getNotifications() } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 1, items = listOf(n1)))

        val vm = NotificationsViewModel(repository, connectivity)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(n1), state.items)
        assertEquals(1L, state.unreadCount)
        assertEquals(false, state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `load failure surfaces errorMessage and isOffline for a network error`() = runTest {
        coEvery { repository.getNotifications() } returns
            ApiResult.Error("Network error", code = "network_unavailable")

        val vm = NotificationsViewModel(repository, connectivity)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Network error", state.errorMessage)
        assertTrue(state.isOffline)
    }

    @Test
    fun `markRead on an unread notification decrements unreadCount and stamps readAt`() = runTest {
        val id = UUID.randomUUID()
        val n1 = notification(id = id)
        coEvery { repository.getNotifications() } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 1, items = listOf(n1)))
        coEvery { repository.markRead(id) } returns ApiResult.Success(Unit)

        val vm = NotificationsViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.markRead(id)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(0L, state.unreadCount)
        assertTrue(state.items.single().readAt != null)
        coVerify(exactly = 1) { repository.markRead(id) }
    }

    @Test
    fun `markRead on an already-read notification does not call the repository`() = runTest {
        val id = UUID.randomUUID()
        val n1 = notification(id = id, readAt = Instant.EPOCH)
        coEvery { repository.getNotifications() } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 0, items = listOf(n1)))

        val vm = NotificationsViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.markRead(id)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.markRead(any()) }
    }

    @Test
    fun `markAllRead clears unreadCount and stamps every item read`() = runTest {
        val n1 = notification()
        val n2 = notification()
        coEvery { repository.getNotifications() } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 2, items = listOf(n1, n2)))
        coEvery { repository.markAllRead() } returns ApiResult.Success(Unit)

        val vm = NotificationsViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.markAllRead()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(0L, state.unreadCount)
        assertTrue(state.items.all { it.readAt != null })
    }

    @Test
    fun `markAllRead is a no-op when unreadCount is already zero`() = runTest {
        coEvery { repository.getNotifications() } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 0, items = emptyList()))

        val vm = NotificationsViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.markAllRead()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.markAllRead() }
    }

    @Test
    fun `reconnecting after a network error reloads automatically`() = runTest {
        networkAvailable.value = false
        coEvery { repository.getNotifications() } returnsMany listOf(
            ApiResult.Error("Network error", code = "network_unavailable"),
            ApiResult.Success(NotificationListResponseDto(unreadCount = 0, items = emptyList())),
        )
        val vm = NotificationsViewModel(repository, connectivity)
        advanceUntilIdle()

        networkAvailable.value = true
        advanceUntilIdle()

        assertNull(vm.uiState.value.errorMessage)
        coVerify(exactly = 2) { repository.getNotifications() }
    }

    @Test
    fun `refresh sets isRefreshing then clears it on success`() = runTest {
        coEvery { repository.getNotifications() } returns
            ApiResult.Success(NotificationListResponseDto(unreadCount = 0, items = emptyList()))

        val vm = NotificationsViewModel(repository, connectivity)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isRefreshing)
        coVerify(exactly = 2) { repository.getNotifications() }
    }
}
