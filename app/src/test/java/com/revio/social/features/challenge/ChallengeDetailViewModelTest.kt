package com.revio.social.features.challenge

import androidx.lifecycle.SavedStateHandle
import com.revio.social.MainDispatcherRule
import com.revio.social.core.feedback.PostCreationSignal
import com.revio.social.core.feedback.PostRemovalSignal
import com.revio.social.core.navigation.Screen
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.Challenge
import com.revio.social.data.model.ChallengeContribution
import com.revio.social.data.model.ChallengeProgress
import com.revio.social.data.model.ChallengeProgressDetail
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.ParticipantState
import com.revio.social.data.model.RewardState
import com.revio.social.data.repository.ChallengeRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Covers the plan's §6 pas 4 and pas 4b: [ChallengeDetailUiState.Content.participantState] is
 * mapped from the server progress; `GET /challenges/{id}` now sends the challenge's own
 * [Challenge.effectiveStatus], which wins over the local startsAt/endsAt derivation whenever
 * it's present (pas 4b); when the server is too old to send it (`UNKNOWN`), a
 * `participantState == CANCELLED` challenge is still surfaced as [EffectiveChallengeStatus.CANCELLED]
 * — the transitional fallback from pas 4, described in the plan's §4/§8.5.
 */
class ChallengeDetailViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var challengeRepository: ChallengeRepository
    private lateinit var clock: Clock
    private lateinit var postCreationSignal: PostCreationSignal
    private lateinit var postRemovalSignal: PostRemovalSignal

    private val now = Instant.parse("2026-08-07T12:00:00Z")
    private val challengeId: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @Before
    fun setup() {
        challengeRepository = mockk()
        clock = Clock.fixed(now, ZoneOffset.UTC)
        postCreationSignal = PostCreationSignal()
        postRemovalSignal = PostRemovalSignal()
    }

    private fun savedStateHandle() = SavedStateHandle(
        mapOf(Screen.ChallengeDetail.ARG_CHALLENGE_ID to challengeId.toString()),
    )

    private fun challenge(
        startsAt: Instant = now.minusSeconds(3600),
        endsAt: Instant = now.plusSeconds(3600),
        effectiveStatus: EffectiveChallengeStatus = EffectiveChallengeStatus.UNKNOWN,
    ) = Challenge(
        id = challengeId,
        title = "Weekend Golf Hunt",
        description = null,
        targetFamilyBrand = "Volkswagen",
        targetFamilyName = "Golf",
        requiredPosts = 5,
        rewardPoints = 300,
        startsAt = startsAt,
        endsAt = endsAt,
        effectiveStatus = effectiveStatus,
    )

    private fun progressDetail(
        participantState: ParticipantState,
        contributionCount: Int = 3,
        rewardState: RewardState = RewardState.NONE,
        contributions: List<ChallengeContribution> = emptyList(),
    ) = ChallengeProgressDetail(
        progress = ChallengeProgress(
            contributionCount = contributionCount,
            rewardState = rewardState,
            participantState = participantState,
        ),
        contributions = contributions,
    )

    private fun buildViewModel() = ChallengeDetailViewModel(
        savedStateHandle(),
        challengeRepository,
        clock,
        postCreationSignal,
        postRemovalSignal,
    )

    @Test
    fun `participantState din progres se propaga in Content`() {
        coEvery { challengeRepository.getChallenge(challengeId) } returns ApiResult.Success(challenge())
        coEvery { challengeRepository.getChallengeProgress(challengeId) } returns
            ApiResult.Success(progressDetail(participantState = ParticipantState.COMPLETED_PENDING))

        val viewModel = buildViewModel()

        val state = viewModel.uiState.value
        assertEquals(true, state is ChallengeDetailUiState.Content)
        state as ChallengeDetailUiState.Content
        assertEquals(ParticipantState.COMPLETED_PENDING, state.participantState)
    }

    @Test
    fun `progres fara participantState (server vechi) - ParticipantState UNKNOWN`() {
        coEvery { challengeRepository.getChallenge(challengeId) } returns ApiResult.Success(challenge())
        coEvery { challengeRepository.getChallengeProgress(challengeId) } returns
            ApiResult.Success(progressDetail(participantState = ParticipantState.UNKNOWN))

        val viewModel = buildViewModel()

        val state = viewModel.uiState.value as ChallengeDetailUiState.Content
        assertEquals(ParticipantState.UNKNOWN, state.participantState)
    }

    @Test
    fun `participantState CANCELLED - effectiveStatus devine CANCELLED desi fereastra ar fi ACTIVE`() {
        // startsAt/endsAt would derive ACTIVE on their own — CANCELLED must win regardless.
        coEvery { challengeRepository.getChallenge(challengeId) } returns
            ApiResult.Success(challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600)))
        coEvery { challengeRepository.getChallengeProgress(challengeId) } returns
            ApiResult.Success(progressDetail(participantState = ParticipantState.CANCELLED))

        val viewModel = buildViewModel()

        val state = viewModel.uiState.value as ChallengeDetailUiState.Content
        assertEquals(EffectiveChallengeStatus.CANCELLED, state.effectiveStatus)
        assertEquals(ParticipantState.CANCELLED, state.participantState)
    }

    @Test
    fun `challenge activ fara CANCELLED - effectiveStatus ramane derivat din startsAt-endsAt`() {
        coEvery { challengeRepository.getChallenge(challengeId) } returns
            ApiResult.Success(challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600)))
        coEvery { challengeRepository.getChallengeProgress(challengeId) } returns
            ApiResult.Success(progressDetail(participantState = ParticipantState.IN_PROGRESS))

        val viewModel = buildViewModel()

        val state = viewModel.uiState.value as ChallengeDetailUiState.Content
        assertEquals(EffectiveChallengeStatus.ACTIVE, state.effectiveStatus)
    }

    @Test
    fun `challenge effectiveStatus de la server castiga in fata derivarii locale din date`() {
        // startsAt/endsAt would derive ACTIVE on their own — the server-sent status must win.
        coEvery { challengeRepository.getChallenge(challengeId) } returns
            ApiResult.Success(
                challenge(
                    startsAt = now.minusSeconds(3600),
                    endsAt = now.plusSeconds(3600),
                    effectiveStatus = EffectiveChallengeStatus.ENDED,
                ),
            )
        coEvery { challengeRepository.getChallengeProgress(challengeId) } returns
            ApiResult.Success(progressDetail(participantState = ParticipantState.IN_PROGRESS))

        val viewModel = buildViewModel()

        val state = viewModel.uiState.value as ChallengeDetailUiState.Content
        assertEquals(EffectiveChallengeStatus.ENDED, state.effectiveStatus)
    }

    @Test
    fun `challenge effectiveStatus de la server castiga si in fata fallback-ului CANCELLED din participantState`() {
        coEvery { challengeRepository.getChallenge(challengeId) } returns
            ApiResult.Success(
                challenge(
                    startsAt = now.minusSeconds(3600),
                    endsAt = now.plusSeconds(3600),
                    effectiveStatus = EffectiveChallengeStatus.ACTIVE,
                ),
            )
        coEvery { challengeRepository.getChallengeProgress(challengeId) } returns
            ApiResult.Success(progressDetail(participantState = ParticipantState.CANCELLED))

        val viewModel = buildViewModel()

        val state = viewModel.uiState.value as ChallengeDetailUiState.Content
        assertEquals(EffectiveChallengeStatus.ACTIVE, state.effectiveStatus)
    }
}
