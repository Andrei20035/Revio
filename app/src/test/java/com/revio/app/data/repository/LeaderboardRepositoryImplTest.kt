package com.revio.app.data.repository

import com.revio.app.core.network.ApiResult
import com.revio.app.data.remote.api.LeaderboardApi
import com.revio.app.data.remote.dto.leaderboard.CurrentUserStandingDto
import com.revio.app.data.remote.dto.leaderboard.LeaderboardEntryDto
import com.revio.app.data.remote.dto.leaderboard.LeaderboardResponseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class LeaderboardRepositoryImplTest {

    private val api: LeaderboardApi = mockk()
    private lateinit var repository: LeaderboardRepositoryImpl

    private val entryDto = LeaderboardEntryDto(
        userId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        rank = 1,
        username = "alice",
        avatarUrl = "https://cdn.example.com/alice.jpg",
        spotScore = 2000,
        streakDays = 5,
    )

    private val responseDto = LeaderboardResponseDto(
        currentUser = CurrentUserStandingDto(entry = entryDto, movement = "UP", placesMoved = 2),
        entries = listOf(entryDto),
    )

    @Before
    fun setUp() {
        repository = LeaderboardRepositoryImpl(api)
    }

    @Test
    fun `getLeaderboard success maps domain correctly`() = runTest {
        coEvery { api.getLeaderboard(any()) } returns Response.success(responseDto)

        val result = repository.getLeaderboard()

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(1, data.entries.size)
        assertEquals("alice", data.entries[0].username)
        assertEquals(2000, data.entries[0].spotScore)
        assertEquals(5, data.entries[0].streakDays)
        assertEquals("alice", data.currentUser.entry.username)
    }

    @Test
    fun `getLeaderboard 401 returns ApiResult Error`() = runTest {
        val errorBody = """{"error":"Unauthorized"}""".toResponseBody("application/json".toMediaType())
        coEvery { api.getLeaderboard(any()) } returns Response.error(401, errorBody)

        val result = repository.getLeaderboard()

        assertTrue(result is ApiResult.Error)
        assertEquals("Unauthorized", (result as ApiResult.Error).message)
    }

    @Test
    fun `getLeaderboard network error returns ApiResult Error`() = runTest {
        coEvery { api.getLeaderboard(any()) } throws IOException("Connection refused")

        val result = repository.getLeaderboard()

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).message.contains("Network error"))
    }
}
