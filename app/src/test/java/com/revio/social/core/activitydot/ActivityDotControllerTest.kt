package com.revio.social.core.activitydot

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.remote.dto.notification.NotificationCategory
import com.revio.social.data.remote.dto.notification.NotificationDto
import com.revio.social.data.remote.dto.notification.NotificationListResponseDto
import com.revio.social.data.remote.dto.notification.NotificationType
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
import org.junit.Assert.assertNull
import org.junit.Test

/** Mutable [Clock] test double — lets a throttle test advance "now" between two [ActivityDotController.refresh] calls. */
private class MutableClock(startInstant: Instant) : Clock() {
    var instant: Instant = startInstant
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instant
}

class ActivityDotControllerTest {

    private val userId: UUID = UUID.randomUUID()
    private val otherUserId: UUID = UUID.randomUUID()

    private fun notification(updatedAt: Instant, createdAt: Instant = updatedAt): NotificationDto = NotificationDto(
        id = UUID.randomUUID(),
        type = NotificationType.SOCIAL,
        title = "title",
        body = "body",
        blocking = false,
        createdAt = createdAt,
        updatedAt = updatedAt,
        category = NotificationCategory.LIKES,
    )

    private fun response(vararg items: NotificationDto): NotificationListResponseDto =
        NotificationListResponseDto(unreadCount = items.size.toLong(), items = items.toList())

    /** A [UserPreferences] mock plus the real backing flow for one user's `activityLastSeenAt`, so a persisted write is directly observable. */
    private class PrefsFixture(val prefs: UserPreferences, val lastSeenFlow: MutableStateFlow<Instant?>)

    private fun userPreferences(
        forUserId: UUID = userId,
        lastSeenAt: Instant? = null,
        userIdFlow: MutableStateFlow<UUID?> = MutableStateFlow(forUserId),
    ): PrefsFixture {
        val lastSeenFlow = MutableStateFlow(lastSeenAt)
        val prefs = mockk<UserPreferences>(relaxed = true).apply {
            every { this@apply.userId } returns userIdFlow
            every { activityLastSeenAt(forUserId) } returns lastSeenFlow
            coEvery { setActivityLastSeenAt(forUserId, any()) } coAnswers {
                lastSeenFlow.value = it.invocation.args[1] as Instant
            }
        }
        return PrefsFixture(prefs, lastSeenFlow)
    }

    private fun notificationRepository(initial: ApiResult<NotificationListResponseDto>): NotificationRepository =
        mockk<NotificationRepository>().apply {
            coEvery { getNotifications(any()) } returns initial
        }

    /** Never transitions offline -> online, so [ActivityDotController]'s reconnect trigger stays silent unless a test wires its own. */
    private fun defaultConnectivity(): NetworkConnectivityManager =
        mockk<NetworkConnectivityManager>().apply {
            every { isNetworkAvailable } returns MutableStateFlow(false)
        }

    private fun controller(
        notificationRepository: NotificationRepository,
        prefsFixture: PrefsFixture,
        clock: Clock = Clock.systemUTC(),
        connectivity: NetworkConnectivityManager = defaultConnectivity(),
    ) = ActivityDotController(notificationRepository, prefsFixture.prefs, clock, connectivity)

    @Test
    fun `no lastSeen watermark and non-empty items lights the dot`() = runTest {
        val now = Instant.parse("2026-08-25T10:00:00Z")
        val prefs = userPreferences(lastSeenAt = null)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = now))))
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.refresh(force = true)

        assertEquals(true, ctrl.hasUnseenActivity.value)
    }

    @Test
    fun `latestUpdatedAt after lastSeen lights the dot`() = runTest {
        val lastSeen = Instant.parse("2026-08-25T10:00:00Z")
        val prefs = userPreferences(lastSeenAt = lastSeen)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = lastSeen.plusSeconds(60)))))
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.refresh(force = true)

        assertEquals(true, ctrl.hasUnseenActivity.value)
    }

    @Test
    fun `latestUpdatedAt equal to lastSeen keeps the dot off`() = runTest {
        val lastSeen = Instant.parse("2026-08-25T10:00:00Z")
        val prefs = userPreferences(lastSeenAt = lastSeen)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = lastSeen))))
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.refresh(force = true)

        assertEquals(false, ctrl.hasUnseenActivity.value)
    }

    @Test
    fun `onActivityOpened turns the dot off and persists the server watermark`() = runTest {
        val latest = Instant.parse("2026-08-25T10:05:00Z")
        val prefs = userPreferences(lastSeenAt = null)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = latest))))
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.refresh(force = true)
        assertEquals(true, ctrl.hasUnseenActivity.value)

        ctrl.onActivityOpened()

        // Immediate optimistic flip, before the persist below even lands.
        assertEquals(false, ctrl.hasUnseenActivity.value)
        coVerify(timeout = 1000) { prefs.prefs.setActivityLastSeenAt(userId, latest) }
    }

    @Test
    fun `onActivityOpened called twice in a row is idempotent`() = runTest {
        val latest = Instant.parse("2026-08-25T10:05:00Z")
        val prefs = userPreferences(lastSeenAt = null)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = latest))))
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.refresh(force = true)

        ctrl.onActivityOpened()
        ctrl.onActivityOpened()

        assertEquals(false, ctrl.hasUnseenActivity.value)
        coVerify(timeout = 1000) { prefs.prefs.setActivityLastSeenAt(userId, latest) }
        // The second call didn't corrupt the persisted watermark with a different value.
        assertEquals(latest, prefs.lastSeenFlow.value)
    }

    @Test
    fun `onActivityOpened before any successful fetch defers the persist to the next refresh`() = runTest {
        val latest = Instant.parse("2026-08-25T10:05:00Z")
        val prefs = userPreferences(lastSeenAt = null)
        // Gate the repo response so the init-triggered automatic refresh() hasn't completed yet
        // when onActivityOpened() runs below — reproducing "opened Activity while offline, or
        // before the first fetch returned".
        val gate = CompletableDeferred<ApiResult<NotificationListResponseDto>>()
        val repo = mockk<NotificationRepository>().apply {
            coEvery { getNotifications(any()) } coAnswers { gate.await() }
        }
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.onActivityOpened()

        // Optimistic flip still happens immediately even with no watermark to persist yet.
        assertEquals(false, ctrl.hasUnseenActivity.value)
        coVerify(exactly = 0) { prefs.prefs.setActivityLastSeenAt(any(), any()) }

        gate.complete(ApiResult.Success(response(notification(updatedAt = latest))))
        ctrl.refresh(force = true)

        // The deferred open is honored by the first fetch that lands: the watermark is stamped
        // with its updatedAt, and the dot does NOT light up for the very activity the user had
        // already opened Activity for.
        coVerify(timeout = 1000) { prefs.prefs.setActivityLastSeenAt(userId, latest) }
        assertEquals(false, ctrl.hasUnseenActivity.value)
    }

    @Test
    fun `sub-millisecond server precision does not relight the dot after onActivityOpened`() = runTest {
        // Server updatedAt carries microsecond precision (Postgres TIMESTAMP); the persisted
        // watermark is millisecond precision (Instant epoch millis in UserPreferences). Without
        // truncating latestUpdatedAt to milliseconds before comparing/persisting, this same
        // updatedAt would look newer than what was just persisted, and the dot would relight.
        val latest = Instant.parse("2026-08-25T10:05:00.123456Z")
        val prefs = userPreferences(lastSeenAt = null)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = latest))))
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.refresh(force = true)
        ctrl.onActivityOpened()
        coVerify(timeout = 1000) { prefs.prefs.setActivityLastSeenAt(userId, any()) }
        assertEquals(false, ctrl.hasUnseenActivity.value)

        // Same underlying notification refetched (same updatedAt) after the watermark was persisted.
        ctrl.refresh(force = true)

        assertEquals(false, ctrl.hasUnseenActivity.value)
    }

    @Test
    fun `an aggregated row whose updatedAt moved past lastSeen lights the dot even though createdAt did not`() = runTest {
        val createdAt = Instant.parse("2026-08-25T09:00:00Z")
        val lastSeen = createdAt.plusSeconds(30 * 60) // user opened Activity 30 min after the row was first created...
        val updatedAt = createdAt.plusSeconds(60 * 60) // ...but a new actor joined the aggregation 60 min after creation.
        val prefs = userPreferences(lastSeenAt = lastSeen)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = updatedAt, createdAt = createdAt))))
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.refresh(force = true)

        assertEquals(true, ctrl.hasUnseenActivity.value)
    }

    @Test
    fun `switching userId does not transfer dot state to the new user`() = runTest {
        val latest = Instant.parse("2026-08-25T10:05:00Z")
        val userIdFlow = MutableStateFlow<UUID?>(userId)
        val prefs = userPreferences(forUserId = userId, lastSeenAt = null, userIdFlow = userIdFlow)
        // Second user's own watermark, stubbed on the same mock instance.
        val otherLastSeenFlow = MutableStateFlow<Instant?>(null)
        every { prefs.prefs.activityLastSeenAt(otherUserId) } returns otherLastSeenFlow
        coEvery { prefs.prefs.setActivityLastSeenAt(otherUserId, any()) } coAnswers {
            otherLastSeenFlow.value = it.invocation.args[1] as Instant
        }
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = latest))))
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.refresh(force = true)
        assertEquals(true, ctrl.hasUnseenActivity.value)
        ctrl.onActivityOpened()
        coVerify(timeout = 1000) { prefs.prefs.setActivityLastSeenAt(userId, latest) }

        userIdFlow.value = otherUserId

        // The second user's own (unseen) watermark must independently light the dot again —
        // it must not inherit the first user's "seen" state.
        coVerify(timeout = 1000) { prefs.prefs.activityLastSeenAt(otherUserId) }
        assertEquals(true, ctrl.hasUnseenActivity.value)
    }

    @Test
    fun `a network error on refresh keeps the previous state and does not crash`() = runTest {
        val latest = Instant.parse("2026-08-25T10:05:00Z")
        val prefs = userPreferences(lastSeenAt = null)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = latest))))
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.refresh(force = true)
        assertEquals(true, ctrl.hasUnseenActivity.value)
        val previousSnapshot = ctrl.lastSnapshot.value

        coEvery { repo.getNotifications(any()) } returns ApiResult.Error("network_unavailable")
        ctrl.refresh(force = true)

        assertEquals(true, ctrl.hasUnseenActivity.value)
        assertEquals(previousSnapshot, ctrl.lastSnapshot.value)
    }

    @Test
    fun `two refresh calls 30 seconds apart make a single network call`() = runTest {
        val clock = MutableClock(Instant.parse("2026-08-25T10:00:00Z"))
        val prefs = userPreferences(lastSeenAt = null)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = clock.instant))))
        val ctrl = controller(repo, prefs, clock)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        clock.instant = clock.instant.plusSeconds(30)

        ctrl.refresh(force = false)

        coVerify(exactly = 1) { repo.getNotifications(any()) }
    }

    @Test
    fun `two refresh calls 3 minutes apart make two network calls`() = runTest {
        val clock = MutableClock(Instant.parse("2026-08-25T10:00:00Z"))
        val prefs = userPreferences(lastSeenAt = null)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = clock.instant))))
        val ctrl = controller(repo, prefs, clock)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        clock.instant = clock.instant.plusSeconds(3 * 60)

        ctrl.refresh(force = false)

        coVerify(exactly = 2) { repo.getNotifications(any()) }
    }

    @Test
    fun `refresh with force true bypasses the throttle`() = runTest {
        val clock = MutableClock(Instant.parse("2026-08-25T10:00:00Z"))
        val prefs = userPreferences(lastSeenAt = null)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = clock.instant))))
        val ctrl = controller(repo, prefs, clock)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        clock.instant = clock.instant.plusSeconds(10) // well within the throttle window

        ctrl.refresh(force = true)

        coVerify(exactly = 2) { repo.getNotifications(any()) }
    }

    @Test
    fun `lastSnapshot reflects the latest successful response`() = runTest {
        val prefs = userPreferences(lastSeenAt = null)
        val firstResponse = response(notification(updatedAt = Instant.parse("2026-08-25T10:00:00Z")))
        val repo = notificationRepository(ApiResult.Success(firstResponse))
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.refresh(force = true)
        assertEquals(firstResponse, ctrl.lastSnapshot.value)

        val secondResponse = response(notification(updatedAt = Instant.parse("2026-08-25T11:00:00Z")))
        coEvery { repo.getNotifications(any()) } returns ApiResult.Success(secondResponse)
        ctrl.refresh(force = true)

        assertEquals(secondResponse, ctrl.lastSnapshot.value)
    }

    @Test
    fun `no items ever fetched keeps the dot off`() = runTest {
        val prefs = userPreferences(lastSeenAt = null)
        val repo = notificationRepository(ApiResult.Success(response()))
        val ctrl = controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        ctrl.refresh(force = true)

        assertEquals(false, ctrl.hasUnseenActivity.value)
        assertNull(ctrl.lastSnapshot.value?.items?.firstOrNull())
    }

    @Test
    fun `emitting a non-null userId triggers an automatic refresh on start`() = runTest {
        val prefs = userPreferences(lastSeenAt = null)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = Instant.parse("2026-08-25T10:00:00Z")))))

        controller(repo, prefs)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
    }

    @Test
    fun `a null userId never triggers getNotifications`() = runTest {
        val userIdFlow = MutableStateFlow<UUID?>(null)
        val prefs = userPreferences(userIdFlow = userIdFlow)
        val repo = notificationRepository(ApiResult.Success(response()))

        controller(repo, prefs)

        // Structural guarantee, not a timing race: init{}'s collector only calls refresh() when
        // userId != null, so there is no code path here that could ever call getNotifications.
        coVerify(exactly = 0) { repo.getNotifications(any()) }
    }

    @Test
    fun `onReconnected triggers a refresh even while the throttle is active`() = runTest {
        val clock = MutableClock(Instant.parse("2026-08-25T10:00:00Z"))
        val prefs = userPreferences(lastSeenAt = null)
        val repo = notificationRepository(ApiResult.Success(response(notification(updatedAt = clock.instant))))
        val networkAvailable = MutableStateFlow(false)
        val connectivity = mockk<NetworkConnectivityManager>().apply {
            every { isNetworkAvailable } returns networkAvailable
        }
        controller(repo, prefs, clock, connectivity)

        coVerify(timeout = 1000) { repo.getNotifications(any()) }
        clock.instant = clock.instant.plusSeconds(10) // well within the 2-minute throttle window

        networkAvailable.value = true // offline -> online transition

        coVerify(timeout = 1000, exactly = 2) { repo.getNotifications(any()) }
    }
}
