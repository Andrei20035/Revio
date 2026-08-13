package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.isNetworkError
import com.revio.social.data.model.ChallengeAdminStatus
import com.revio.social.data.remote.api.AdminChallengeApi
import com.revio.social.data.remote.dto.admin.challenge.ChallengeAdminDto
import com.revio.social.data.remote.dto.admin.challenge.ChallengeAdminPageDto
import com.revio.social.data.remote.dto.admin.challenge.CreateChallengeAdminRequestDto
import com.revio.social.data.remote.dto.admin.challenge.FinalizationResultDto
import com.revio.social.data.remote.dto.admin.challenge.RevokeAllRequestDto
import com.revio.social.data.remote.dto.admin.challenge.RevokeResultDto
import io.mockk.coEvery
import io.mockk.coVerify
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
import java.time.Instant
import java.util.UUID

class AdminChallengeRepositoryImplTest {

    private val api: AdminChallengeApi = mockk()
    private lateinit var repository: AdminChallengeRepositoryImpl

    private val challengeId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val familyId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val challengeAdminDto = ChallengeAdminDto(
        id = challengeId,
        title = "Weekend Golf Hunt",
        description = "Find every Golf you can this weekend",
        targetFamilyId = familyId,
        requiredPosts = 5,
        rewardPoints = 300,
        startsAt = Instant.parse("2026-08-07T00:00:00Z"),
        endsAt = Instant.parse("2026-08-09T00:00:00Z"),
        adminTimezone = "Europe/Bucharest",
        status = "DRAFT",
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    @Before
    fun setUp() {
        repository = AdminChallengeRepositoryImpl(api)
    }

    @Test
    fun `createChallenge success maps domain correctly`() = runTest {
        coEvery { api.createChallenge(any()) } returns Response.success(challengeAdminDto)

        val result = repository.createChallenge(
            title = "Weekend Golf Hunt",
            description = "Find every Golf you can this weekend",
            targetFamilyId = familyId,
            requiredPosts = 5,
            rewardPoints = 300,
            startsAtLocal = "2026-08-07T09:00:00",
            endsAtLocal = "2026-08-09T22:00:00",
            timezone = "Europe/Bucharest",
        )

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals("Weekend Golf Hunt", data.title)
        assertEquals(familyId, data.targetFamilyId)
        assertEquals(ChallengeAdminStatus.DRAFT, data.status)

        coVerify {
            api.createChallenge(
                CreateChallengeAdminRequestDto(
                    title = "Weekend Golf Hunt",
                    description = "Find every Golf you can this weekend",
                    targetFamilyId = familyId,
                    requiredPosts = 5,
                    rewardPoints = 300,
                    startsAtLocal = "2026-08-07T09:00:00",
                    endsAtLocal = "2026-08-09T22:00:00",
                    timezone = "Europe/Bucharest",
                ),
            )
        }
    }

    @Test
    fun `createChallenge 400 returns ApiResult Error`() = runTest {
        val errorBody = """{"error":"Invalid request"}""".toResponseBody("application/json".toMediaType())
        coEvery { api.createChallenge(any()) } returns Response.error(400, errorBody)

        val result = repository.createChallenge(
            title = "Weekend Golf Hunt",
            description = null,
            targetFamilyId = familyId,
            requiredPosts = 5,
            rewardPoints = 300,
            startsAtLocal = "2026-08-07T09:00:00",
            endsAtLocal = "2026-08-09T22:00:00",
            timezone = "Europe/Bucharest",
        )

        assertTrue(result is ApiResult.Error)
        assertEquals("Invalid request", (result as ApiResult.Error).message)
    }

    @Test
    fun `listChallenges success maps page correctly`() = runTest {
        val pageDto = ChallengeAdminPageDto(challenges = listOf(challengeAdminDto), nextCursor = null, hasMore = false)
        coEvery { api.listChallenges(any(), any(), any(), any()) } returns Response.success(pageDto)

        val result = repository.listChallenges(limit = 20, status = "SCHEDULED")

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(1, data.challenges.size)
        assertEquals("Weekend Golf Hunt", data.challenges[0].title)
        assertEquals(false, data.hasMore)
    }

    @Test
    fun `listChallenges passes cursor as string query params`() = runTest {
        val pageDto = ChallengeAdminPageDto(challenges = emptyList(), nextCursor = null, hasMore = false)
        coEvery { api.listChallenges(any(), any(), any(), any()) } returns Response.success(pageDto)
        val cursorCreatedAt = Instant.parse("2026-08-01T00:00:00Z")

        repository.listChallenges(limit = 20, cursorCreatedAt = cursorCreatedAt, cursorId = challengeId)

        coVerify {
            api.listChallenges(
                limit = 20,
                cursorCreatedAt = cursorCreatedAt.toString(),
                cursorId = challengeId.toString(),
                status = null,
            )
        }
    }

    @Test
    fun `getChallenge success maps domain correctly`() = runTest {
        coEvery { api.getChallenge(challengeId) } returns Response.success(challengeAdminDto)

        val result = repository.getChallenge(challengeId)

        assertTrue(result is ApiResult.Success)
        assertEquals(challengeId, (result as ApiResult.Success).data.id)
    }

    @Test
    fun `getChallenge 404 returns ApiResult Error`() = runTest {
        val errorBody = """{"error":"Challenge not found"}""".toResponseBody("application/json".toMediaType())
        coEvery { api.getChallenge(challengeId) } returns Response.error(404, errorBody)

        val result = repository.getChallenge(challengeId)

        assertTrue(result is ApiResult.Error)
        assertEquals("Challenge not found", (result as ApiResult.Error).message)
    }

    @Test
    fun `updateChallengeTitle success maps domain correctly`() = runTest {
        coEvery { api.updateChallengeTitle(challengeId, any()) } returns Response.success(challengeAdminDto)

        val result = repository.updateChallengeTitle(challengeId, "New title", "New description")

        assertTrue(result is ApiResult.Success)
        coVerify { api.updateChallengeTitle(challengeId, match { it.title == "New title" }) }
    }

    @Test
    fun `publishChallenge success maps domain correctly`() = runTest {
        coEvery { api.publishChallenge(challengeId) } returns Response.success(challengeAdminDto)

        val result = repository.publishChallenge(challengeId)

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `publishChallenge 409 overlap returns ApiResult Error`() = runTest {
        val errorBody = """{"error":"Challenge overlaps another scheduled challenge"}"""
            .toResponseBody("application/json".toMediaType())
        coEvery { api.publishChallenge(challengeId) } returns Response.error(409, errorBody)

        val result = repository.publishChallenge(challengeId)

        assertTrue(result is ApiResult.Error)
        assertEquals("Challenge overlaps another scheduled challenge", (result as ApiResult.Error).message)
    }

    @Test
    fun `cancelChallenge success maps revoke result correctly`() = runTest {
        coEvery { api.cancelChallenge(challengeId) } returns Response.success(RevokeResultDto(revokedCount = 4))

        val result = repository.cancelChallenge(challengeId)

        assertTrue(result is ApiResult.Success)
        assertEquals(4, (result as ApiResult.Success).data.revokedCount)
    }

    @Test
    fun `revokeAllRewards sends confirmChallengeId matching the path id`() = runTest {
        coEvery { api.revokeAllRewards(challengeId, any()) } returns Response.success(RevokeResultDto(revokedCount = 2))

        val result = repository.revokeAllRewards(challengeId)

        assertTrue(result is ApiResult.Success)
        coVerify { api.revokeAllRewards(challengeId, RevokeAllRequestDto(confirmChallengeId = challengeId)) }
    }

    @Test
    fun `finalizeChallenge success maps finalization result correctly`() = runTest {
        coEvery { api.finalizeChallenge(challengeId) } returns
            Response.success(FinalizationResultDto(grantedCount = 3, revokedCount = 1))

        val result = repository.finalizeChallenge(challengeId)

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(3, data.grantedCount)
        assertEquals(1, data.revokedCount)
    }

    @Test
    fun `finalizeChallenge network error returns ApiResult Error with network code`() = runTest {
        coEvery { api.finalizeChallenge(challengeId) } throws IOException("Connection refused")

        val result = repository.finalizeChallenge(challengeId)

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).isNetworkError)
    }
}
