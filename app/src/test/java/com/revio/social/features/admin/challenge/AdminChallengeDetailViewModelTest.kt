package com.revio.social.features.admin.challenge

import androidx.lifecycle.SavedStateHandle
import com.revio.social.MainDispatcherRule
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.AdminChallenge
import com.revio.social.data.model.AdminChallengeFinalizationResult
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AdminChallengeDetailViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repository: AdminChallengeRepository

    private val challengeId = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val familyId = UUID.fromString("00000000-0000-0000-0000-0000000000f1")

    private val draftChallenge = AdminChallenge(
        id = challengeId,
        title = "Weekend Golf Hunt",
        description = null,
        targetFamilyId = familyId,
        requiredPosts = 5,
        rewardPoints = 300,
        startsAt = Instant.parse("2026-08-07T00:00:00Z"),
        endsAt = Instant.parse("2026-08-09T00:00:00Z"),
        adminTimezone = "Europe/Bucharest",
        status = ChallengeAdminStatus.DRAFT,
        createdBy = null,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
        publishedAt = null,
        cancelledAt = null,
        finalizedAt = null,
    )

    private fun savedStateHandle() = SavedStateHandle(
        mapOf(ADMIN_CHALLENGE_DETAIL_ARG_CHALLENGE_ID to challengeId.toString()),
    )

    @Before
    fun setup() {
        repository = mockk()
        coEvery { repository.getChallenge(challengeId) } returns ApiResult.Success(draftChallenge)
    }

    @Test
    fun `publish cu succes actualizeaza uiState cu challenge-ul publicat`() {
        val publishedChallenge = draftChallenge.copy(status = ChallengeAdminStatus.SCHEDULED)
        coEvery { repository.publishChallenge(challengeId) } returns ApiResult.Success(publishedChallenge)

        val viewModel = AdminChallengeDetailViewModel(savedStateHandle(), repository)
        viewModel.publish()

        val state = viewModel.uiState.value
        assert(state is AdminChallengeDetailUiState.Content)
        assertEquals(ChallengeAdminStatus.SCHEDULED, (state as AdminChallengeDetailUiState.Content).challenge.status)
        assertFalse(viewModel.publishState.value.isSubmitting)
        assertNull(viewModel.publishState.value.errorMessage)
    }

    @Test
    fun `publish cu conflict 409 de suprapunere afiseaza mesajul prietenos`() {
        coEvery { repository.publishChallenge(challengeId) } returns
            ApiResult.Error("Challenge $challengeId overlaps another SCHEDULED challenge's window")

        val viewModel = AdminChallengeDetailViewModel(savedStateHandle(), repository)
        viewModel.publish()

        assertEquals(
            "This window overlaps another scheduled challenge.",
            viewModel.publishState.value.errorMessage,
        )
        assertFalse(viewModel.publishState.value.isSubmitting)
        // The stale DRAFT content is preserved so the sheet stays open with the error inline.
        assert(viewModel.uiState.value is AdminChallengeDetailUiState.Content)
    }

    @Test
    fun `publish cu eroare care nu e overlap pastreaza mesajul original`() {
        coEvery { repository.publishChallenge(challengeId) } returns ApiResult.Error("Challenge not found")

        val viewModel = AdminChallengeDetailViewModel(savedStateHandle(), repository)
        viewModel.publish()

        assertEquals("Challenge not found", viewModel.publishState.value.errorMessage)
    }

    @Test
    fun `consumePublishError goleste mesajul de eroare`() {
        coEvery { repository.publishChallenge(challengeId) } returns
            ApiResult.Error("Challenge $challengeId overlaps another SCHEDULED challenge's window")

        val viewModel = AdminChallengeDetailViewModel(savedStateHandle(), repository)
        viewModel.publish()
        viewModel.consumePublishError()

        assertNull(viewModel.publishState.value.errorMessage)
    }

    @Test
    fun `finalize cu succes reincarca detaliul`() {
        val finalizedChallenge = draftChallenge.copy(
            status = ChallengeAdminStatus.SCHEDULED,
            finalizedAt = Instant.parse("2026-08-09T00:05:00Z"),
        )
        coEvery { repository.finalizeChallenge(challengeId) } returns
            ApiResult.Success(AdminChallengeFinalizationResult(grantedCount = 3, revokedCount = 0))
        coEvery { repository.getChallenge(challengeId) } returnsMany listOf(
            ApiResult.Success(draftChallenge),
            ApiResult.Success(finalizedChallenge),
        )

        val viewModel = AdminChallengeDetailViewModel(savedStateHandle(), repository)
        viewModel.finalize()

        val state = viewModel.uiState.value
        assert(state is AdminChallengeDetailUiState.Content)
        assertEquals(
            Instant.parse("2026-08-09T00:05:00Z"),
            (state as AdminChallengeDetailUiState.Content).challenge.finalizedAt,
        )
        assertFalse(viewModel.finalizeState.value.isSubmitting)
        assertNull(viewModel.finalizeState.value.errorMessage)
        // Once for the initial load, once more for the reload after a successful finalize.
        coVerify(exactly = 2) { repository.getChallenge(challengeId) }
    }

    @Test
    fun `finalize cu eroare afiseaza mesajul inline, fara reload`() {
        coEvery { repository.finalizeChallenge(challengeId) } returns ApiResult.Error("Server error")

        val viewModel = AdminChallengeDetailViewModel(savedStateHandle(), repository)
        viewModel.finalize()

        assertEquals("Server error", viewModel.finalizeState.value.errorMessage)
        assertFalse(viewModel.finalizeState.value.isSubmitting)
        // Only the initial load — a failed finalize does not trigger a reload.
        coVerify(exactly = 1) { repository.getChallenge(challengeId) }
    }
}
