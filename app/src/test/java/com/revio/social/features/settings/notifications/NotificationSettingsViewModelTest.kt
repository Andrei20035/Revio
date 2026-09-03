package com.revio.social.features.settings.notifications

import com.revio.social.MainDispatcherRule
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.network.ApiResult
import com.revio.social.core.notifications.NotificationPermissionState
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.remote.dto.notification.NotificationPrefsDto
import com.revio.social.data.remote.dto.notification.UpdateNotificationPrefsRequest
import com.revio.social.data.repository.NotificationPrefsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val notificationPrefsRepository: NotificationPrefsRepository = mockk()
    private val permissionState: NotificationPermissionState = mockk()
    private val userPreferences: UserPreferences = mockk(relaxed = true)
    private val analyticsClient: AnalyticsClient = mockk(relaxed = true)
    private val testUserId: UUID = UUID.randomUUID()

    private fun prefsDto(
        likes: Boolean = true,
        comments: Boolean = true,
        discovery: Boolean = true,
        reminders: Boolean = true,
        challenges: Boolean = true,
    ) = NotificationPrefsDto(
        likesEnabled = likes,
        commentsEnabled = comments,
        discoveryEnabled = discovery,
        remindersEnabled = reminders,
        challengesEnabled = challenges,
    )

    private fun createViewModel(
        everRequested: Boolean = false,
        notificationsEnabled: Boolean = true,
        hasPermission: Boolean = true,
        blockedChannels: Set<String> = emptySet(),
        prefsResult: ApiResult<NotificationPrefsDto> = ApiResult.Success(prefsDto()),
    ): NotificationSettingsViewModel {
        every { userPreferences.userId } returns flowOf(testUserId)
        coEvery { userPreferences.notificationPermissionRequested(testUserId) } returns everRequested
        every { permissionState.areNotificationsEnabled() } returns notificationsEnabled
        every { permissionState.hasPostNotificationsPermission() } returns hasPermission
        every { permissionState.isChannelBlocked(any()) } answers { firstArg<String>() in blockedChannels }
        coEvery { notificationPrefsRepository.getPreferences() } returns prefsResult

        return NotificationSettingsViewModel(
            notificationPrefsRepository = notificationPrefsRepository,
            permissionState = permissionState,
            userPreferences = userPreferences,
            analyticsClient = analyticsClient,
        )
    }

    @Test
    fun `system status is ENABLED when notifications are enabled at OS level`() = runTest {
        val vm = createViewModel(notificationsEnabled = true)
        advanceUntilIdle()

        assertEquals(SystemNotificationsStatus.ENABLED, vm.uiState.value.systemStatus)
    }

    @Test
    fun `system status is NOT_ENABLED when permission was never requested`() = runTest {
        val vm = createViewModel(
            notificationsEnabled = false,
            hasPermission = false,
            everRequested = false,
        )
        advanceUntilIdle()

        assertEquals(SystemNotificationsStatus.NOT_ENABLED, vm.uiState.value.systemStatus)
    }

    @Test
    fun `system status is DISABLED when permission was requested but notifications are off`() = runTest {
        val vm = createViewModel(
            notificationsEnabled = false,
            hasPermission = false,
            everRequested = true,
        )
        advanceUntilIdle()

        assertEquals(SystemNotificationsStatus.DISABLED, vm.uiState.value.systemStatus)
    }

    @Test
    fun `switches stay non-interactive until prefs have loaded even if system status is ENABLED`() = runTest {
        val pending = kotlinx.coroutines.CompletableDeferred<ApiResult<NotificationPrefsDto>>()
        every { userPreferences.userId } returns flowOf(testUserId)
        coEvery { userPreferences.notificationPermissionRequested(testUserId) } returns false
        every { permissionState.areNotificationsEnabled() } returns true
        every { permissionState.hasPostNotificationsPermission() } returns true
        every { permissionState.isChannelBlocked(any()) } returns false
        coEvery { notificationPrefsRepository.getPreferences() } coAnswers { pending.await() }

        val vm = NotificationSettingsViewModel(
            notificationPrefsRepository = notificationPrefsRepository,
            permissionState = permissionState,
            userPreferences = userPreferences,
            analyticsClient = analyticsClient,
        )
        advanceUntilIdle()

        assertFalse(vm.uiState.value.switchesInteractive)

        pending.complete(ApiResult.Success(prefsDto()))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.switchesInteractive)
    }

    @Test
    fun `switches stay non-interactive when system status is not ENABLED`() = runTest {
        val vm = createViewModel(notificationsEnabled = false, hasPermission = false, everRequested = true)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.switchesInteractive)
    }

    @Test
    fun `toggling a category sends an optimistic update and keeps it on success`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery {
            notificationPrefsRepository.updatePreferences(UpdateNotificationPrefsRequest(likesEnabled = false))
        } returns ApiResult.Success(prefsDto(likes = false))

        vm.setLikesEnabled(false)

        assertFalse(vm.uiState.value.likes.enabled)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.likes.enabled)
        coVerify(exactly = 1) {
            notificationPrefsRepository.updatePreferences(UpdateNotificationPrefsRequest(likesEnabled = false))
        }
    }

    @Test
    fun `toggling a category rolls back on repository error`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery {
            notificationPrefsRepository.updatePreferences(UpdateNotificationPrefsRequest(commentsEnabled = false))
        } returns ApiResult.Error("Network error")

        vm.setCommentsEnabled(false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.comments.enabled)
    }

    @Test
    fun `toggling a category is a no-op while switches are not interactive`() = runTest {
        val vm = createViewModel(notificationsEnabled = false, hasPermission = false, everRequested = true)
        advanceUntilIdle()

        vm.setLikesEnabled(false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.likes.enabled)
        coVerify(exactly = 0) { notificationPrefsRepository.updatePreferences(any()) }
    }

    @Test
    fun `toggling challenges sends an optimistic update and keeps it on success`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery {
            notificationPrefsRepository.updatePreferences(UpdateNotificationPrefsRequest(challengesEnabled = false))
        } returns ApiResult.Success(prefsDto(challenges = false))

        vm.setChallengesEnabled(false)

        assertFalse(vm.uiState.value.challenges.enabled)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.challenges.enabled)
        coVerify(exactly = 1) {
            notificationPrefsRepository.updatePreferences(UpdateNotificationPrefsRequest(challengesEnabled = false))
        }
    }

    @Test
    fun `toggling challenges rolls back on repository error`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery {
            notificationPrefsRepository.updatePreferences(UpdateNotificationPrefsRequest(challengesEnabled = false))
        } returns ApiResult.Error("Network error")

        vm.setChallengesEnabled(false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.challenges.enabled)
    }

    @Test
    fun `loaded preferences reflect challengesEnabled from the server`() = runTest {
        val vm = createViewModel(prefsResult = ApiResult.Success(prefsDto(challenges = false)))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.challenges.enabled)
    }

    @Test
    fun `refreshSystemState logs a newly blocked channel only once`() = runTest {
        val vm = createViewModel(blockedChannels = emptySet())
        advanceUntilIdle()
        assertFalse(vm.uiState.value.likes.blockedByChannel)

        every { permissionState.isChannelBlocked("likes") } returns true
        vm.refreshSystemState()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.likes.blockedByChannel)
        verify(exactly = 1) {
            analyticsClient.log(match { it.name == "push_channel_blocked_detected" })
        }

        vm.refreshSystemState()
        advanceUntilIdle()
        verify(exactly = 1) {
            analyticsClient.log(match { it.name == "push_channel_blocked_detected" })
        }
    }

    @Test
    fun `onPermissionRequestResult persists the flag and refreshes system state`() = runTest {
        val vm = createViewModel(notificationsEnabled = false, hasPermission = false, everRequested = false)
        advanceUntilIdle()
        assertEquals(SystemNotificationsStatus.NOT_ENABLED, vm.uiState.value.systemStatus)

        coEvery { userPreferences.notificationPermissionRequested(testUserId) } returns true
        vm.onPermissionRequestResult(granted = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { userPreferences.setNotificationPermissionRequested(testUserId) }
        assertEquals(SystemNotificationsStatus.DISABLED, vm.uiState.value.systemStatus)
    }
}
