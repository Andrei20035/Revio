package com.revio.social.features.profile.dashboard

import androidx.lifecycle.ViewModelStore
import com.revio.social.MainDispatcherRule
import com.revio.social.core.feedback.PostCreationSignal
import com.revio.social.core.feedback.PostRemovalReason
import com.revio.social.core.feedback.PostRemovalSignal
import com.revio.social.core.feedback.PostRemovedEvent
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.Challenge
import com.revio.social.data.model.ChallengeHistoryItem
import com.revio.social.data.model.ChallengeProgress
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.MyChallenges
import com.revio.social.data.model.RewardState
import com.revio.social.data.repository.ChallengeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Covers the plan's pas 3 (docs/plans/analizeaz-bugul-urm-tor-i-warm-toucan.md): the Profile
 * "My Challenges" card must re-fetch `/challenges/me` after a post it counted is removed, whether
 * by the author or by moderation — otherwise it keeps showing stale progress while the Feed card
 * (wired to a matching signal) already corrected itself.
 *
 * Every test disposes the view model through a [ViewModelStore] before returning — while the
 * card is [MyChallengesEntryUiState.Active], `observeCountdown`'s minute ticker keeps
 * rescheduling itself on `viewModelScope` forever by design, and since `runTest` shares its
 * scheduler with [MainDispatcherRule]'s `Dispatchers.Main` here, an un-cancelled ticker would
 * make `runTest`'s idle-drain spin forever. `ViewModelStore.clear()` cancels `viewModelScope`
 * (the same mechanism `onCleared()` uses), which stops it — mirrors ChallengeViewModelTest.
 */
class MyChallengesEntryViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var challengeRepository: ChallengeRepository
    private lateinit var postCreationSignal: PostCreationSignal
    private lateinit var postRemovalSignal: PostRemovalSignal

    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC)
    private val challengeId: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @Before
    fun setup() {
        challengeRepository = mockk()
        postCreationSignal = PostCreationSignal()
        postRemovalSignal = PostRemovalSignal()
    }

    private fun challenge() = Challenge(
        id = challengeId,
        title = "Weekend Golf Hunt",
        description = null,
        targetFamilyBrand = "Volkswagen",
        targetFamilyName = "Golf",
        requiredPosts = 3,
        rewardPoints = 300,
        startsAt = Instant.parse("2026-08-07T00:00:00Z"),
        endsAt = Instant.parse("2026-08-08T00:00:00Z"),
    )

    private fun myChallenges(contributionCount: Int) = MyChallenges(
        summary = null,
        challenges = listOf(
            ChallengeHistoryItem(
                challenge = challenge(),
                effectiveStatus = EffectiveChallengeStatus.ACTIVE,
                progress = ChallengeProgress(contributionCount, RewardState.NONE),
            ),
        ),
        hasMore = false,
        nextCursorEndsAt = null,
        nextCursorId = null,
    )

    private fun buildVm() = MyChallengesEntryViewModel(
        challengeRepository = challengeRepository,
        clock = clock,
        postCreationSignal = postCreationSignal,
        postRemovalSignal = postRemovalSignal,
    )

    @Test
    fun `3-3 urmat de PostRemovedEvent - refresh aduce cardul la 2-3`() = runTest {
        coEvery { challengeRepository.getMyChallenges(limit = 1) } returns ApiResult.Success(myChallenges(3))

        val vm = buildVm()
        val store = ViewModelStore().apply { put("entry", vm) }
        try {
            val initial = vm.uiState.value as MyChallengesEntryUiState.Active
            assertEquals(3, initial.contributionCount)

            val removedPostId = UUID.randomUUID()
            coEvery { challengeRepository.getMyChallenges(limit = 1) } returns ApiResult.Success(myChallenges(2))
            postRemovalSignal.emit(PostRemovedEvent(removedPostId, PostRemovalReason.SelfDelete))

            val after = vm.uiState.value as MyChallengesEntryUiState.Active
            assertEquals(2, after.contributionCount)
            coVerify(exactly = 2) { challengeRepository.getMyChallenges(limit = 1) }
        } finally {
            store.clear()
        }
    }

    @Test
    fun `acelasi postId emis de doua ori - un singur refresh suplimentar`() = runTest {
        coEvery { challengeRepository.getMyChallenges(limit = 1) } returns ApiResult.Success(myChallenges(3))

        val vm = buildVm()
        val store = ViewModelStore().apply { put("entry", vm) }
        try {
            coVerify(exactly = 1) { challengeRepository.getMyChallenges(limit = 1) }

            val removedPostId = UUID.randomUUID()
            coEvery { challengeRepository.getMyChallenges(limit = 1) } returns ApiResult.Success(myChallenges(2))
            postRemovalSignal.emit(PostRemovedEvent(removedPostId, PostRemovalReason.Moderation))
            coVerify(exactly = 2) { challengeRepository.getMyChallenges(limit = 1) }

            // Same postId again — the dedupe guard blocks a third fetch.
            postRemovalSignal.emit(PostRemovedEvent(removedPostId, PostRemovalReason.Moderation))
            coVerify(exactly = 2) { challengeRepository.getMyChallenges(limit = 1) }

            val state = vm.uiState.value as MyChallengesEntryUiState.Active
            assertEquals(2, state.contributionCount)
        } finally {
            store.clear()
        }
    }

    @Test
    fun `refresh esuat dupa PostRemovedEvent - pastreaza ultima stare cunoscuta 3-3`() = runTest {
        coEvery { challengeRepository.getMyChallenges(limit = 1) } returns ApiResult.Success(myChallenges(3))

        val vm = buildVm()
        val store = ViewModelStore().apply { put("entry", vm) }
        try {
            val initial = vm.uiState.value as MyChallengesEntryUiState.Active
            assertEquals(3, initial.contributionCount)

            coEvery { challengeRepository.getMyChallenges(limit = 1) } returns ApiResult.Error("network down")
            postRemovalSignal.emit(PostRemovedEvent(UUID.randomUUID(), PostRemovalReason.SelfDelete))

            val state = vm.uiState.value
            assertTrue(state is MyChallengesEntryUiState.Active)
            assertEquals(3, (state as MyChallengesEntryUiState.Active).contributionCount)
        } finally {
            store.clear()
        }
    }
}
