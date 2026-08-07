package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.isNetworkError
import com.revio.social.data.model.RewardState
import com.revio.social.data.remote.api.ChallengeApi
import com.revio.social.data.remote.dto.challenge.ChallengeDto
import com.revio.social.data.remote.dto.challenge.ChallengeProgressDto
import com.revio.social.data.remote.dto.challenge.CurrentChallengeDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.util.UUID

class ChallengeRepositoryImplTest {

    private val api: ChallengeApi = mockk()
    private lateinit var repository: ChallengeRepositoryImpl

    private val challengeDto = ChallengeDto(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        title = "Weekend Golf Hunt",
        description = "Find every Golf you can this weekend",
        targetFamilyBrand = "Volkswagen",
        targetFamilyName = "Golf",
        requiredPosts = 5,
        rewardPoints = 300,
        startsAt = Instant.parse("2026-08-07T00:00:00Z"),
        endsAt = Instant.parse("2026-08-09T00:00:00Z"),
    )

    private val currentChallengeDto = CurrentChallengeDto(
        challenge = challengeDto,
        progress = ChallengeProgressDto(contributionCount = 3, rewardState = "NONE"),
    )

    @Before
    fun setUp() {
        repository = ChallengeRepositoryImpl(api)
    }

    @Test
    fun `getCurrentChallenge success maps domain correctly`() = runTest {
        coEvery { api.getCurrentChallenge() } returns Response.success(currentChallengeDto)

        val result = repository.getCurrentChallenge()

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals("Weekend Golf Hunt", data.challenge?.title)
        assertEquals("Volkswagen", data.challenge?.targetFamilyBrand)
        assertEquals(5, data.challenge?.requiredPosts)
        assertEquals(3, data.progress?.contributionCount)
        assertEquals(RewardState.NONE, data.progress?.rewardState)
    }

    @Test
    fun `getCurrentChallenge with null challenge maps to null domain fields`() = runTest {
        coEvery { api.getCurrentChallenge() } returns Response.success(CurrentChallengeDto())

        val result = repository.getCurrentChallenge()

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertNull(data.challenge)
        assertNull(data.progress)
    }

    @Test
    fun `getCurrentChallenge 401 returns ApiResult Error`() = runTest {
        val errorBody = """{"error":"Invalid userId claim"}""".toResponseBody("application/json".toMediaType())
        coEvery { api.getCurrentChallenge() } returns Response.error(401, errorBody)

        val result = repository.getCurrentChallenge()

        assertTrue(result is ApiResult.Error)
        assertEquals("Invalid userId claim", (result as ApiResult.Error).message)
    }

    @Test
    fun `getCurrentChallenge 500 returns ApiResult Error`() = runTest {
        val errorBody = "Internal Server Error".toResponseBody("text/plain".toMediaType())
        coEvery { api.getCurrentChallenge() } returns Response.error(500, errorBody)

        val result = repository.getCurrentChallenge()

        assertTrue(result is ApiResult.Error)
    }

    @Test
    fun `getCurrentChallenge network error returns ApiResult Error with network code`() = runTest {
        coEvery { api.getCurrentChallenge() } throws IOException("Connection refused")

        val result = repository.getCurrentChallenge()

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).isNetworkError)
    }
}
