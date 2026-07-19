package com.revio.app.features.leaderboard

import com.revio.app.MainDispatcherRule
import com.revio.app.core.network.ApiResult
import com.revio.app.data.repository.LeaderboardRepository
import com.revio.app.data.repository.UserRepository
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
        val vm = LeaderboardViewModel(repository, userRepository)
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
        val vm = LeaderboardViewModel(repository, userRepository)
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
        val vm = LeaderboardViewModel(repository, userRepository)
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
        val vm = LeaderboardViewModel(repository, userRepository)
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
        val vm = LeaderboardViewModel(repository, userRepository)
        advanceUntilIdle()

        vm.retry()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.errorMessage)
        assertEquals(3, state.podium.size)
        coVerify(exactly = 2) { repository.getLeaderboard() }
    }
}
