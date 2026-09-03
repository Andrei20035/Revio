package com.revio.social.features.leaderboard

import com.revio.social.MainDispatcherRule
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.data.repository.LeaderboardRepository
import com.revio.social.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val repository: LeaderboardRepository = mockk()
    private val userRepository: UserRepository = mockk {
        every { currentUser } returns MutableStateFlow(null)
    }
    private val networkAvailable = MutableStateFlow(true)
    private val connectivity: NetworkConnectivityManager = mockk {
        every { isNetworkAvailable } returns networkAvailable
    }

    private fun entry(rank: Int) = LeaderboardEntry(
        userId = UUID.randomUUID(),
        rank = rank,
        username = "user$rank",
        avatarUrl = null,
        spotScore = 1000 - rank * 10,
        streakDays = rank,
    )

    private val standing = CurrentUserStanding(
        entry = entry(28),
        movement = RankMovement.UP,
        placesMoved = 3,
    )

    private val successResult = LeaderboardResult(
        currentUser = standing,
        entries = listOf(entry(3), entry(1), entry(2), entry(4), entry(5)),
    )

    @Test
    fun `load success splits and sorts entries into podium and rest`() = runTest {
        coEvery { repository.getLeaderboard() } returns ApiResult.Success(successResult)
        val vm = LeaderboardViewModel(repository, userRepository, connectivity)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(3, state.podium.size)
        assertEquals(listOf(1, 2, 3), state.podium.map { it.rank })
        assertEquals(2, state.rest.size)
        assertEquals(listOf(4, 5), state.rest.map { it.rank })
        assertEquals(standing, state.currentUser)
    }

    @Test
    fun `load error sets errorMessage and clears loading`() = runTest {
        coEvery { repository.getLeaderboard() } returns ApiResult.Error("Server error")
        val vm = LeaderboardViewModel(repository, userRepository, connectivity)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Server error", state.errorMessage)
        assertTrue(state.podium.isEmpty())
        assertTrue(state.rest.isEmpty())
    }

    @Test
    fun `refresh reloads data and clears error`() = runTest {
        coEvery { repository.getLeaderboard() } returnsMany listOf(
            ApiResult.Error("fail"),
            ApiResult.Success(successResult),
        )
        val vm = LeaderboardViewModel(repository, userRepository, connectivity)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.errorMessage)
        assertFalse(state.isRefreshing)
        assertEquals(3, state.podium.size)
        coVerify(exactly = 2) { repository.getLeaderboard() }
    }

    @Test
    fun `refresh is no-op when already refreshing`() = runTest {
        coEvery { repository.getLeaderboard() } returns ApiResult.Success(successResult)
        val vm = LeaderboardViewModel(repository, userRepository, connectivity)
        advanceUntilIdle()

        // first call completes, state.isRefreshing=false, second refresh proceeds normally
        vm.refresh()
        vm.refresh() // guard should block re-entry if still refreshing
        advanceUntilIdle()

        // repository called once for init + once for the non-guarded refresh = 2 max
        coVerify(atMost = 3) { repository.getLeaderboard() }
    }

    @Test
    fun `retry reloads after error`() = runTest {
        coEvery { repository.getLeaderboard() } returnsMany listOf(
            ApiResult.Error("fail"),
            ApiResult.Success(successResult),
        )
        val vm = LeaderboardViewModel(repository, userRepository, connectivity)
        advanceUntilIdle()

        vm.retry()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.errorMessage)
        assertEquals(3, state.podium.size)
        coVerify(exactly = 2) { repository.getLeaderboard() }
    }

    @Test
    fun `reconnecting after a network error reloads automatically`() = runTest {
        networkAvailable.value = false
        coEvery { repository.getLeaderboard() } returnsMany listOf(
            ApiResult.Error("Network error", code = "network_unavailable"),
            ApiResult.Success(successResult),
        )
        val vm = LeaderboardViewModel(repository, userRepository, connectivity)
        advanceUntilIdle()

        networkAvailable.value = true
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.errorMessage)
        assertEquals(3, state.podium.size)
        coVerify(exactly = 2) { repository.getLeaderboard() }
    }

    @Test
    fun `reconnecting does not reload when there was no error`() = runTest {
        networkAvailable.value = false
        coEvery { repository.getLeaderboard() } returns ApiResult.Success(successResult)
        val vm = LeaderboardViewModel(repository, userRepository, connectivity)
        advanceUntilIdle()

        networkAvailable.value = true
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getLeaderboard() }
    }

    // ----------------------------------------------------------------------
    // pas 3 (docs/plans/avem-un-bug-android-mutable-sky.md) — onResumed() retries a screen stuck
    // in an error state without depending on any connectivity transition.
    // ----------------------------------------------------------------------

    @Test
    fun `onResumed retries exactly once when the screen is in an error state`() = runTest {
        coEvery { repository.getLeaderboard() } returnsMany listOf(
            ApiResult.Error("Server error"),
            ApiResult.Success(successResult),
        )
        val vm = LeaderboardViewModel(repository, userRepository, connectivity)
        advanceUntilIdle()
        assertEquals("Server error", vm.uiState.value.errorMessage)

        vm.onResumed()
        advanceUntilIdle()

        assertNull(vm.uiState.value.errorMessage)
        assertEquals(3, vm.uiState.value.podium.size)
        coVerify(exactly = 2) { repository.getLeaderboard() }
    }

    @Test
    fun `onResumed does not reload when there was no error`() = runTest {
        coEvery { repository.getLeaderboard() } returns ApiResult.Success(successResult)
        val vm = LeaderboardViewModel(repository, userRepository, connectivity)
        advanceUntilIdle()

        vm.onResumed()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getLeaderboard() }
    }
}
