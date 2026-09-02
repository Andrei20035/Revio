package com.revio.social.core.notices

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.remote.dto.notification.NotificationCategory
import com.revio.social.data.remote.dto.notification.NotificationListResponseDto
import com.revio.social.data.repository.NotificationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mutable [Clock] test double — lets a throttle test advance "now" between two [NoticesUnreadController.refresh] calls. */
private class MutableClock(startInstant: Instant) : Clock() {
    var instant: Instant = startInstant
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instant
}

class NoticesUnreadControllerTest {

    private val userId: UUID = UUID.randomUUID()
    private val otherUserId: UUID = UUID.randomUUID()

    private fun response(unreadCount: Long): NotificationListResponseDto =
        NotificationListResponseDto(unreadCount = unreadCount, items = emptyList())

    private fun userPreferences(userIdFlow: MutableStateFlow<UUID?> = MutableStateFlow(userId)): UserPreferences =
        mockk<UserPreferences>(relaxed = true).apply {
            every { this@apply.userId } returns userIdFlow
        }

    /** Never transitions offline -> online, so the controller's reconnect trigger stays silent unless a test wires its own. */
    private fun defaultConnectivity(): NetworkConnectivityManager =
        mockk<NetworkConnectivityManager>().apply {
            every { isNetworkAvailable } returns MutableStateFlow(false)
        }

    private fun notificationRepository(
        getResult: ApiResult<NotificationListResponseDto> = ApiResult.Success(response(0)),
        markAllReadResult: ApiResult<Unit> = ApiResult.Success(Unit),
    ): NotificationRepository = mockk<NotificationRepository>().apply {
        coEvery { getNotifications(any(), any(), any(), any()) } returns getResult
        coEvery { markAllRead(any()) } returns markAllReadResult
    }

    private fun controller(
        notificationRepository: NotificationRepository,
        userPreferences: UserPreferences,
        clock: Clock = Clock.systemUTC(),
        connectivity: NetworkConnectivityManager = defaultConnectivity(),
    ) = NoticesUnreadController(notificationRepository, userPreferences, clock, connectivity)

    @Test
    fun `refresh sets unreadCount from the ACCOUNT category response`() = runTest {
        val repo = notificationRepository(getResult = ApiResult.Success(response(3)))
        val prefs = userPreferences()
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any(), any(), any(), any()) }
        ctrl.refresh(force = true)

        assertEquals(3L, ctrl.unreadCount.value)
        coVerify { repo.getNotifications(category = NotificationCategory.ACCOUNT) }
    }

    @Test
    fun `onNoticesOpened zeroes unreadCount immediately, before the mark-all-read request resolves`() = runTest {
        val repo = notificationRepository(getResult = ApiResult.Success(response(5)))
        val prefs = userPreferences()
        val ctrl = controller(repo, prefs)
        coVerify(timeout = 1000) { repo.getNotifications(any(), any(), any(), any()) }
        ctrl.refresh(force = true)
        assertEquals(5L, ctrl.unreadCount.value)

        // Gate the mark-all-read response so the optimistic zero can be observed before it lands.
        val gate = CompletableDeferred<ApiResult<Unit>>()
        coEvery { repo.markAllRead(any()) } coAnswers { gate.await() }

        ctrl.onNoticesOpened()

        assertEquals(0L, ctrl.unreadCount.value)
        gate.complete(ApiResult.Success(Unit))
    }

    @Test
    fun `a failed mark-all-read does not relight the dot`() = runTest {
        val repo = notificationRepository(
            getResult = ApiResult.Success(response(2)),
            markAllReadResult = ApiResult.Error("network_unavailable"),
        )
        val prefs = userPreferences()
        val ctrl = controller(repo, prefs)
        coVerify(timeout = 1000) { repo.getNotifications(any(), any(), any(), any()) }
        ctrl.refresh(force = true)

        ctrl.onNoticesOpened()

        coVerify(timeout = 1000) { repo.markAllRead(category = NotificationCategory.ACCOUNT) }
        assertEquals(0L, ctrl.unreadCount.value)
    }

    @Test
    fun `a failed mark-all-read is retried exactly once on reconnect`() = runTest {
        val networkAvailable = MutableStateFlow(false)
        val connectivity = mockk<NetworkConnectivityManager>().apply {
            every { isNetworkAvailable } returns networkAvailable
        }
        val repo = notificationRepository(
            getResult = ApiResult.Success(response(1)),
            markAllReadResult = ApiResult.Error("network_unavailable"),
        )
        val prefs = userPreferences()
        val ctrl = controller(repo, prefs, connectivity = connectivity)
        coVerify(timeout = 1000) { repo.getNotifications(any(), any(), any(), any()) }
        ctrl.refresh(force = true)
        ctrl.onNoticesOpened()
        coVerify(timeout = 1000) { repo.markAllRead(category = NotificationCategory.ACCOUNT) }

        networkAvailable.value = true // offline -> online transition

        coVerify(timeout = 1000, exactly = 2) { repo.markAllRead(category = NotificationCategory.ACCOUNT) }
    }

    @Test
    fun `a successful retry on reconnect clears the pending flag so a later reconnect does not retry again`() = runTest {
        val networkAvailable = MutableStateFlow(false)
        val connectivity = mockk<NetworkConnectivityManager>().apply {
            every { isNetworkAvailable } returns networkAvailable
        }
        val repo = notificationRepository(
            getResult = ApiResult.Success(response(1)),
            markAllReadResult = ApiResult.Error("network_unavailable"),
        )
        val prefs = userPreferences()
        val ctrl = controller(repo, prefs, connectivity = connectivity)
        coVerify(timeout = 1000) { repo.getNotifications(any(), any(), any(), any()) }
        ctrl.refresh(force = true)
        ctrl.onNoticesOpened()
        coVerify(timeout = 1000) { repo.markAllRead(category = NotificationCategory.ACCOUNT) }

        // First reconnect: retry succeeds this time.
        coEvery { repo.markAllRead(any()) } returns ApiResult.Success(Unit)
        networkAvailable.value = true
        coVerify(timeout = 1000, exactly = 2) { repo.markAllRead(category = NotificationCategory.ACCOUNT) }

        // Second reconnect: nothing pending, so no further markAllRead call.
        networkAvailable.value = false
        networkAvailable.value = true

        coVerify(timeout = 1000, exactly = 2) { repo.markAllRead(category = NotificationCategory.ACCOUNT) }
    }

    @Test
    fun `switching userId resets unreadCount to the new user's own state`() = runTest {
        val userIdFlow = MutableStateFlow<UUID?>(userId)
        val prefs = userPreferences(userIdFlow)
        val repo = notificationRepository(getResult = ApiResult.Success(response(4)))
        val ctrl = controller(repo, prefs)
        coVerify(timeout = 1000) { repo.getNotifications(any(), any(), any(), any()) }
        ctrl.refresh(force = true)
        assertEquals(4L, ctrl.unreadCount.value)

        coEvery { repo.getNotifications(any(), any(), any(), any()) } returns ApiResult.Success(response(0))
        userIdFlow.value = otherUserId

        // Calls so far: (1) automatic refresh on controller init, (2) the explicit refresh above,
        // (3) the automatic refresh triggered by this user switch.
        coVerify(timeout = 1000, exactly = 3) { repo.getNotifications(any(), any(), any(), any()) }
        assertEquals(0L, ctrl.unreadCount.value)
    }

    @Test
    fun `a network error on refresh keeps the previous unreadCount and does not crash`() = runTest {
        val repo = notificationRepository(getResult = ApiResult.Success(response(7)))
        val prefs = userPreferences()
        val ctrl = controller(repo, prefs)
        coVerify(timeout = 1000) { repo.getNotifications(any(), any(), any(), any()) }
        ctrl.refresh(force = true)
        assertEquals(7L, ctrl.unreadCount.value)

        coEvery { repo.getNotifications(any(), any(), any(), any()) } returns ApiResult.Error("network_unavailable")
        ctrl.refresh(force = true)

        assertEquals(7L, ctrl.unreadCount.value)
    }

    @Test
    fun `two refresh calls 30 seconds apart make a single network call`() = runTest {
        val clock = MutableClock(Instant.parse("2026-08-25T10:00:00Z"))
        val repo = notificationRepository(getResult = ApiResult.Success(response(1)))
        val prefs = userPreferences()
        val ctrl = controller(repo, prefs, clock)
        coVerify(timeout = 1000) { repo.getNotifications(any(), any(), any(), any()) }
        clock.instant = clock.instant.plusSeconds(30)

        ctrl.refresh(force = false)

        coVerify(exactly = 1) { repo.getNotifications(any(), any(), any(), any()) }
    }

    @Test
    fun `two refresh calls 3 minutes apart make two network calls`() = runTest {
        val clock = MutableClock(Instant.parse("2026-08-25T10:00:00Z"))
        val repo = notificationRepository(getResult = ApiResult.Success(response(1)))
        val prefs = userPreferences()
        val ctrl = controller(repo, prefs, clock)
        coVerify(timeout = 1000) { repo.getNotifications(any(), any(), any(), any()) }
        clock.instant = clock.instant.plusSeconds(3 * 60)

        ctrl.refresh(force = false)

        coVerify(exactly = 2) { repo.getNotifications(any(), any(), any(), any()) }
    }

    @Test
    fun `refresh with force true bypasses the throttle`() = runTest {
        val clock = MutableClock(Instant.parse("2026-08-25T10:00:00Z"))
        val repo = notificationRepository(getResult = ApiResult.Success(response(1)))
        val prefs = userPreferences()
        val ctrl = controller(repo, prefs, clock)
        coVerify(timeout = 1000) { repo.getNotifications(any(), any(), any(), any()) }
        clock.instant = clock.instant.plusSeconds(10) // well within the throttle window

        ctrl.refresh(force = true)

        coVerify(exactly = 2) { repo.getNotifications(any(), any(), any(), any()) }
    }

    @Test
    fun `calling onNoticesOpened twice in a row is idempotent`() = runTest {
        val repo = notificationRepository(getResult = ApiResult.Success(response(1)))
        val prefs = userPreferences()
        val ctrl = controller(repo, prefs)
        coVerify(timeout = 1000) { repo.getNotifications(any(), any(), any(), any()) }
        ctrl.refresh(force = true)

        ctrl.onNoticesOpened()
        ctrl.onNoticesOpened()

        assertEquals(0L, ctrl.unreadCount.value)
        coVerify(timeout = 1000) { repo.markAllRead(category = NotificationCategory.ACCOUNT) }
    }

    @Test
    fun `a null userId never triggers getNotifications`() = runTest {
        val userIdFlow = MutableStateFlow<UUID?>(null)
        val prefs = userPreferences(userIdFlow)
        val repo = notificationRepository()

        controller(repo, prefs)

        coVerify(exactly = 0) { repo.getNotifications(any(), any(), any(), any()) }
    }
}
