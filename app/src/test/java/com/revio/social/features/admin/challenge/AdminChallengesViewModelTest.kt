package com.revio.social.features.admin.challenge

import com.revio.social.MainDispatcherRule
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.AdminChallenge
import com.revio.social.data.model.AdminChallengeListCursor
import com.revio.social.data.model.AdminChallengePage
import com.revio.social.data.model.ChallengeAdminStatus
import com.revio.social.data.repository.AdminChallengeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AdminChallengesViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repository: AdminChallengeRepository

    private fun challenge(id: UUID, title: String = "Weekend Golf Hunt") = AdminChallenge(
        id = id,
        title = title,
        description = null,
        targetFamilyId = UUID.fromString("00000000-0000-0000-0000-0000000000f1"),
        requiredPosts = 5,
        rewardPoints = 300,
        startsAt = Instant.parse("2026-08-07T00:00:00Z"),
        endsAt = Instant.parse("2026-08-09T00:00:00Z"),
        adminTimezone = "Europe/Bucharest",
        status = ChallengeAdminStatus.SCHEDULED,
        createdBy = null,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
        publishedAt = null,
        cancelledAt = null,
        finalizedAt = null,
    )

    private val challengeA = challenge(UUID.fromString("00000000-0000-0000-0000-00000000000a"), "Challenge A")
    private val challengeB = challenge(UUID.fromString("00000000-0000-0000-0000-00000000000b"), "Challenge B")

    @Before
    fun setup() {
        repository = mockk()
    }

    @Test
    fun `load populeaza starea cu prima pagina`() {
        coEvery { repository.listChallenges(limit = 20, cursorCreatedAt = null, cursorId = null, status = null) } returns
            ApiResult.Success(AdminChallengePage(challenges = listOf(challengeA), nextCursor = null, hasMore = false))

        val viewModel = AdminChallengesViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals(listOf(challengeA), state.challenges)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `load cu eroare expune mesajul de eroare, lista ramane goala`() {
        coEvery { repository.listChallenges(limit = 20, cursorCreatedAt = null, cursorId = null, status = null) } returns
            ApiResult.Error("Server error")

        val viewModel = AdminChallengesViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state.challenges.isEmpty())
        assertFalse(state.isLoading)
        assertEquals("Server error", state.errorMessage)
    }

    @Test
    fun `load cu eroare de retea seteaza isOffline`() {
        coEvery { repository.listChallenges(limit = 20, cursorCreatedAt = null, cursorId = null, status = null) } returns
            ApiResult.Error("Network error", code = "network_unavailable")

        val viewModel = AdminChallengesViewModel(repository)

        assertTrue(viewModel.uiState.value.isOffline)
    }

    @Test
    fun `retry reincearca load-ul dupa o eroare`() {
        coEvery { repository.listChallenges(limit = 20, cursorCreatedAt = null, cursorId = null, status = null) } returns
            ApiResult.Error("Server error") andThen
            ApiResult.Success(AdminChallengePage(challenges = listOf(challengeA), nextCursor = null, hasMore = false))

        val viewModel = AdminChallengesViewModel(repository)
        assertEquals("Server error", viewModel.uiState.value.errorMessage)

        viewModel.retry()

        val state = viewModel.uiState.value
        assertEquals(listOf(challengeA), state.challenges)
        assertNull(state.errorMessage)
        coVerify(exactly = 2) {
            repository.listChallenges(limit = 20, cursorCreatedAt = null, cursorId = null, status = null)
        }
    }

    @Test
    fun `refresh reincarca prima pagina`() {
        coEvery { repository.listChallenges(limit = 20, cursorCreatedAt = null, cursorId = null, status = null) } returns
            ApiResult.Success(AdminChallengePage(challenges = listOf(challengeA), nextCursor = null, hasMore = false)) andThen
            ApiResult.Success(AdminChallengePage(challenges = listOf(challengeB), nextCursor = null, hasMore = false))

        val viewModel = AdminChallengesViewModel(repository)
        assertEquals(listOf(challengeA), viewModel.uiState.value.challenges)

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(listOf(challengeB), state.challenges)
        coVerify(exactly = 2) {
            repository.listChallenges(limit = 20, cursorCreatedAt = null, cursorId = null, status = null)
        }
    }

    @Test
    fun `loadMore adauga a doua pagina la lista existenta, nu o inlocuieste`() {
        val cursor = AdminChallengeListCursor(
            lastCreatedAt = Instant.parse("2026-08-01T00:00:00Z"),
            lastChallengeId = challengeA.id,
        )
        coEvery { repository.listChallenges(limit = 20, cursorCreatedAt = null, cursorId = null, status = null) } returns
            ApiResult.Success(AdminChallengePage(challenges = listOf(challengeA), nextCursor = cursor, hasMore = true))
        coEvery {
            repository.listChallenges(
                limit = 20,
                cursorCreatedAt = cursor.lastCreatedAt,
                cursorId = cursor.lastChallengeId,
                status = null,
            )
        } returns ApiResult.Success(AdminChallengePage(challenges = listOf(challengeB), nextCursor = null, hasMore = false))

        val viewModel = AdminChallengesViewModel(repository)
        assertEquals(listOf(challengeA), viewModel.uiState.value.challenges)

        viewModel.loadMore()

        val state = viewModel.uiState.value
        assertEquals(listOf(challengeA, challengeB), state.challenges)
        assertFalse(state.isPaging)
        assertFalse(state.hasMore)
    }

    @Test
    fun `loadMore fara hasMore nu declanseaza niciun apel`() {
        coEvery { repository.listChallenges(limit = 20, cursorCreatedAt = null, cursorId = null, status = null) } returns
            ApiResult.Success(AdminChallengePage(challenges = listOf(challengeA), nextCursor = null, hasMore = false))

        val viewModel = AdminChallengesViewModel(repository)
        viewModel.loadMore()

        coVerify(exactly = 1) {
            repository.listChallenges(limit = 20, cursorCreatedAt = null, cursorId = null, status = null)
        }
        assertEquals(listOf(challengeA), viewModel.uiState.value.challenges)
    }

    @Test
    fun `loadMore cu eroare pastreaza pagina curenta si expune eroarea`() {
        val cursor = AdminChallengeListCursor(
            lastCreatedAt = Instant.parse("2026-08-01T00:00:00Z"),
            lastChallengeId = challengeA.id,
        )
        coEvery { repository.listChallenges(limit = 20, cursorCreatedAt = null, cursorId = null, status = null) } returns
            ApiResult.Success(AdminChallengePage(challenges = listOf(challengeA), nextCursor = cursor, hasMore = true))
        coEvery {
            repository.listChallenges(
                limit = 20,
                cursorCreatedAt = cursor.lastCreatedAt,
                cursorId = cursor.lastChallengeId,
                status = null,
            )
        } returns ApiResult.Error("Server error")

        val viewModel = AdminChallengesViewModel(repository)
        viewModel.loadMore()

        val state = viewModel.uiState.value
        assertEquals(listOf(challengeA), state.challenges)
        assertFalse(state.isPaging)
        assertEquals("Server error", state.errorMessage)
    }
}
