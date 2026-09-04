package com.revio.social.features.challenge

import androidx.lifecycle.ViewModelStore
import com.revio.social.MainDispatcherRule
import com.revio.social.core.feedback.PostCreatedEvent
import com.revio.social.core.feedback.PostCreationSignal
import com.revio.social.core.feedback.PostRemovalReason
import com.revio.social.core.feedback.PostRemovalSignal
import com.revio.social.core.feedback.PostRemovedEvent
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.Challenge
import com.revio.social.data.model.ChallengeProgress
import com.revio.social.data.model.CurrentChallenge
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.ParticipantState
import com.revio.social.data.model.RewardState
import com.revio.social.data.repository.ChallengeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Covers the plan's §4 state model + refresh coalescing (test matrix rows 12–19) and the §6
 * countdown ticker + local expiry (rows 20–22): the client-side active/future/ended filter on
 * `/challenges/current` (which can return the *next* challenge, not only the active one), error
 * handling that never surfaces its own error state, the refresh-window deduplication with its
 * pull-to-refresh bypass, and the minute-aligned ticker that only runs while a challenge is
 * [ChallengeUiState.Active] and hides the card locally — with no repository call — once it expires.
 *
 * The rows 12–19 tests run without `runTest`: [MainDispatcherRule]'s default
 * [kotlinx.coroutines.test.UnconfinedTestDispatcher] resolves every mocked suspend call and
 * `viewModelScope.launch` eagerly and synchronously, so asserting `uiState.value` right after
 * constructing the view model is enough. The ticker tests (rows 20–22) need real `delay()`
 * timing under test control, so they swap in a [StandardTestDispatcher] tied to `runTest`'s own
 * `testScheduler` — otherwise the ticker's `delay()` calls would run on a *different*,
 * unconnected virtual clock that `advanceTimeBy` in the test body can't drive (see the plan's
 * §11 risk note for Etapa 5).
 *
 * Those same ticker tests must dispose the view model through a [ViewModelStore] before
 * returning: while a challenge is [ChallengeUiState.Active], the minute ticker keeps
 * rescheduling itself on `viewModelScope` forever by design (it only stops itself on a real
 * state transition to [ChallengeUiState.Hidden]). `runTest` drains its scheduler once the test
 * body finishes, and since `Dispatchers.Main` now shares that same scheduler, an un-cancelled
 * ticker makes that drain spin forever — `ViewModelStore.clear()` cancels `viewModelScope`
 * (the same mechanism the Android framework uses in `onCleared()`), which stops it.
 *
 * The Etapa 9 tests (matrix rows 23–25) cover [PostCreationSignal]'s `replay = 1` pitfall: a
 * fresh collector is always redelivered the *last* event, however old, so the `lastHandledPostId`
 * guard in `observePostCreation` — not the refresh coalescing window — is what must prevent a
 * duplicate refresh for an already-seen `postId`. They reuse the ticker tests' `runTest` +
 * [StandardTestDispatcher] setup since [PostCreationSignal.emit] is a suspend call.
 */
class ChallengeViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var challengeRepository: ChallengeRepository
    private lateinit var clock: MutableClock
    private lateinit var postCreationSignal: PostCreationSignal
    private lateinit var postRemovalSignal: PostRemovalSignal

    private val now = Instant.parse("2026-08-07T12:00:00Z")

    @Before
    fun setup() {
        challengeRepository = mockk()
        clock = MutableClock(now)
        postCreationSignal = PostCreationSignal()
        postRemovalSignal = PostRemovalSignal()
    }

    private fun challenge(
        startsAt: Instant,
        endsAt: Instant,
        requiredPosts: Int = 5,
        rewardPoints: Int = 300,
        id: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
    ) = Challenge(
        id = id,
        title = "Weekend Golf Hunt",
        description = null,
        targetFamilyBrand = "Volkswagen",
        targetFamilyName = "Golf",
        requiredPosts = requiredPosts,
        rewardPoints = rewardPoints,
        startsAt = startsAt,
        endsAt = endsAt,
    )

    @Test
    fun `challenge activ - starea devine Active cu datele corecte`() {
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        val progress = ChallengeProgress(contributionCount = 3, rewardState = RewardState.NONE)
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, progress))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)

        val state = viewModel.uiState.value
        assertTrue(state is ChallengeUiState.Active)
        state as ChallengeUiState.Active
        assertEquals(activeChallenge.id, state.challengeId)
        assertEquals("Spot 5 Volkswagen Golf", state.titleLine)
        assertEquals(3, state.contributionCount)
        assertEquals(5, state.requiredPosts)
        assertEquals(300, state.rewardPoints)
        assertEquals(RewardState.NONE, state.rewardState)
        assertEquals(EffectiveChallengeStatus.ACTIVE, state.effectiveStatus)
        assertEquals(false, state.isStale)
    }

    @Test
    fun `participantState COMPLETED_PENDING - se propaga distinct, nu mai apare ca progres incomplet`() {
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        val progress = ChallengeProgress(
            contributionCount = 5,
            rewardState = RewardState.NONE,
            participantState = ParticipantState.COMPLETED_PENDING,
        )
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, progress))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)

        val state = viewModel.uiState.value
        assertTrue(state is ChallengeUiState.Active)
        state as ChallengeUiState.Active
        assertEquals(ParticipantState.COMPLETED_PENDING, state.participantState)
        assertTrue(state.participantState != ParticipantState.IN_PROGRESS)
    }

    @Test
    fun `progress fara participantState (server vechi) - ParticipantState UNKNOWN`() {
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        val progress = ChallengeProgress(contributionCount = 3, rewardState = RewardState.NONE)
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, progress))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)

        val state = viewModel.uiState.value as ChallengeUiState.Active
        assertEquals(ParticipantState.UNKNOWN, state.participantState)
    }

    @Test
    fun `challenge null - starea ramane Hidden`() {
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(challenge = null, progress = null))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)

        assertEquals(ChallengeUiState.Hidden, viewModel.uiState.value)
    }

    // Bloc J6: a scheduled challenge (startsAt in the future) is surfaced instead of hidden —
    // `/challenges/current` can return the *next* challenge, not only the currently active one,
    // and that upcoming state must now be visible. This deliberately supersedes the old
    // "filtered out client-side" expectation. The "STARTS…" eyebrow copy itself is rendered by
    // ChallengeCard from `state.remaining`/`state.participantState`, so it isn't asserted here.
    @Test
    fun `challenge viitor (startsAt dupa now) - Active, nu mai e ascuns pe client`() {
        val futureChallenge = challenge(startsAt = now.plusSeconds(3600), endsAt = now.plusSeconds(7200))
        val progress = ChallengeProgress(
            contributionCount = 0,
            rewardState = RewardState.NONE,
            participantState = ParticipantState.NOT_STARTED,
        )
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(futureChallenge, progress))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)

        val state = viewModel.uiState.value
        assertTrue(state is ChallengeUiState.Active)
        state as ChallengeUiState.Active
        assertEquals(futureChallenge.id, state.challengeId)
        assertEquals(0, state.contributionCount)
        assertEquals(ParticipantState.NOT_STARTED, state.participantState)
        assertEquals(EffectiveChallengeStatus.SCHEDULED, state.effectiveStatus)
    }

    @Test
    fun `challenge expirat (endsAt inainte de now) - Hidden`() {
        val endedChallenge = challenge(startsAt = now.minusSeconds(7200), endsAt = now.minusSeconds(3600))
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(endedChallenge, ChallengeProgress(5, RewardState.GRANTED)))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)

        assertEquals(ChallengeUiState.Hidden, viewModel.uiState.value)
    }

    @Test
    fun `eroare la incarcarea initiala, fara date anterioare - Hidden`() {
        coEvery { challengeRepository.getCurrentChallenge() } returns ApiResult.Error("boom")

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)

        assertEquals(ChallengeUiState.Hidden, viewModel.uiState.value)
    }

    @Test
    fun `eroare dupa un Active anterior - isStale true, pastreaza datele vechi`() {
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        val progress = ChallengeProgress(contributionCount = 3, rewardState = RewardState.NONE)
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, progress))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        val activeState = viewModel.uiState.value as ChallengeUiState.Active

        // Trece fereastra de coalescing (5s) ca al doilea refresh sa nu fie ignorat.
        clock.advanceBy(Duration.ofSeconds(6))
        coEvery { challengeRepository.getCurrentChallenge() } returns ApiResult.Error("network down")
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertTrue(state is ChallengeUiState.Active)
        state as ChallengeUiState.Active
        assertEquals(true, state.isStale)
        assertEquals(activeState.challengeId, state.challengeId)
        assertEquals(activeState.contributionCount, state.contributionCount)
        assertEquals(activeState.titleLine, state.titleLine)
    }

    @Test
    fun `doua refresh-uri in fereastra de 5s - un singur apel de repository`() {
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, ChallengeProgress(0, RewardState.NONE)))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        // init() a facut deja un apel; astea doua sunt "in fereastra" fata de acel apel.
        viewModel.refresh()
        viewModel.refresh()

        coVerify(exactly = 1) { challengeRepository.getCurrentChallenge() }
    }

    @Test
    fun `pull-to-refresh ocoleste fereastra de coalescing`() {
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, ChallengeProgress(0, RewardState.NONE)))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        // Fara sa avanseze ceasul: un refresh normal ar fi ignorat, pull-to-refresh nu.
        viewModel.refresh(ChallengeRefreshTrigger.PullToRefresh)

        coVerify(exactly = 2) { challengeRepository.getCurrentChallenge() }
    }

    // ---- Ticker de minut + expirare locala (Etapa 5) ----

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `remaining se actualizeaza la granita minutei, fara apel suplimentar de retea`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val activeChallenge = challenge(
            startsAt = now.minusSeconds(3600),
            endsAt = now.plus(Duration.ofHours(5).plusMinutes(20)),
        )
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, ChallengeProgress(0, RewardState.NONE)))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        val store = ViewModelStore().apply { put("challenge", viewModel) }
        try {
            // NU advanceUntilIdle(): tickerul se re-programeaza la infinit (while(true) { emit; delay }),
            // deci scheduler-ul nu ajunge niciodata "idle". runCurrent() ruleaza doar ce e deja gata acum.
            runCurrent()

            val initial = viewModel.uiState.value as ChallengeUiState.Active
            assertEquals(RemainingTime.HoursMinutes(5, 20), initial.remaining)

            clock.advanceBy(Duration.ofMinutes(1))
            advanceTimeBy(Duration.ofMinutes(1).toMillis())
            runCurrent()

            val afterOneMinute = viewModel.uiState.value as ChallengeUiState.Active
            assertEquals(RemainingTime.HoursMinutes(5, 19), afterOneMinute.remaining)
            // Ticker-ul e local: countdown-ul avanseaza fara niciun apel suplimentar de repository.
            coVerify(exactly = 1) { challengeRepository.getCurrentChallenge() }
        } finally {
            // Opreste tickerul (inca activ - challenge-ul nu a expirat) inainte ca runTest sa
            // dreneze scheduler-ul la final; altfel drenajul ar rula la infinit.
            store.clear()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `challenge care expira cat VM-ul e activ trece in Hidden, fara apel suplimentar de repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(90))
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, ChallengeProgress(0, RewardState.NONE)))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        val store = ViewModelStore().apply { put("challenge", viewModel) }
        try {
            runCurrent()
            assertTrue(viewModel.uiState.value is ChallengeUiState.Active)

            // Trece de endsAt (now + 90s) doar local, prin ticker - fara refresh de retea.
            // NU advanceUntilIdle(): tickerul e infinit, deci scheduler-ul nu ar ajunge idle.
            clock.advanceBy(Duration.ofMinutes(2))
            advanceTimeBy(Duration.ofMinutes(2).toMillis())
            runCurrent()

            assertEquals(ChallengeUiState.Hidden, viewModel.uiState.value)
            coVerify(exactly = 1) { challengeRepository.getCurrentChallenge() }
        } finally {
            store.clear()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `tickerul nu porneste (nu citeste ceasul) cand starea e Hidden`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(challenge = null, progress = null))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        val store = ViewModelStore().apply { put("challenge", viewModel) }
        try {
            advanceUntilIdle()
            assertEquals(ChallengeUiState.Hidden, viewModel.uiState.value)
            val instantReadsAfterInit = clock.instantCallCount

            // Daca tickerul ar fi pornit incorect, ar citi clock.instant() la fiecare minut.
            clock.advanceBy(Duration.ofMinutes(10))
            advanceTimeBy(Duration.ofMinutes(10).toMillis())
            advanceUntilIdle()

            assertEquals(ChallengeUiState.Hidden, viewModel.uiState.value)
            assertEquals(instantReadsAfterInit, clock.instantCallCount)
            coVerify(exactly = 1) { challengeRepository.getCurrentChallenge() }
        } finally {
            store.clear()
        }
    }

    // ---- Sincronizare dupa upload (Etapa 9) ----

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `PostCreatedEvent nou dupa fereastra de coalescing - declanseaza un refresh`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, ChallengeProgress(0, RewardState.NONE)))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        val store = ViewModelStore().apply { put("challenge", viewModel) }
        try {
            runCurrent()
            coVerify(exactly = 1) { challengeRepository.getCurrentChallenge() }

            // Trece fereastra de coalescing (5s) ca refresh-ul declansat de eveniment sa nu fie
            // absorbit doar de fereastra de timp - vrem sa testam garda pe postId separat.
            clock.advanceBy(Duration.ofSeconds(6))
            postCreationSignal.emit(PostCreatedEvent(UUID.randomUUID(), 1200L, 0, null))
            runCurrent()

            coVerify(exactly = 2) { challengeRepository.getCurrentChallenge() }
        } finally {
            store.clear()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `acelasi postId emis de doua ori - un singur refresh suplimentar`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, ChallengeProgress(0, RewardState.NONE)))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        val store = ViewModelStore().apply { put("challenge", viewModel) }
        try {
            runCurrent()
            coVerify(exactly = 1) { challengeRepository.getCurrentChallenge() }

            val postId = UUID.randomUUID()
            clock.advanceBy(Duration.ofSeconds(6))
            postCreationSignal.emit(PostCreatedEvent(postId, 1200L, 0, null))
            runCurrent()
            coVerify(exactly = 2) { challengeRepository.getCurrentChallenge() }

            // Acelasi postId, dupa ce fereastra de coalescing ar fi trecut din nou - garda pe
            // postId, nu fereastra de timp, e cea care blocheaza acest al treilea apel.
            clock.advanceBy(Duration.ofSeconds(6))
            postCreationSignal.emit(PostCreatedEvent(postId, 1500L, 1, null))
            runCurrent()

            coVerify(exactly = 2) { challengeRepository.getCurrentChallenge() }
        } finally {
            store.clear()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `eveniment deja in cache-ul de replay la crearea VM-ului - zero refresh suplimentar`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, ChallengeProgress(0, RewardState.NONE)))

        // Un upload "vechi", emis inainte ca acest ViewModel sa existe macar - exact scenariul
        // replay = 1 din PostCreationSignal: orice colector nou primeste ultimul eveniment,
        // oricat de vechi.
        postCreationSignal.emit(PostCreatedEvent(UUID.randomUUID(), 900L, 0, null))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        val store = ViewModelStore().apply { put("challenge", viewModel) }
        try {
            runCurrent()

            assertTrue(viewModel.uiState.value is ChallengeUiState.Active)
            // init() si evenimentul redelivrat cad in aceeasi fereastra de coalescing (ceasul nu
            // a avansat intre ele) - un singur apel total, nu doua.
            coVerify(exactly = 1) { challengeRepository.getCurrentChallenge() }
        } finally {
            store.clear()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `esecul refresh-ului dupa un upload - isStale true, date vechi pastrate, fara exceptie`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        val progress = ChallengeProgress(contributionCount = 3, rewardState = RewardState.NONE)
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, progress))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        val store = ViewModelStore().apply { put("challenge", viewModel) }
        try {
            runCurrent()
            val activeState = viewModel.uiState.value as ChallengeUiState.Active

            clock.advanceBy(Duration.ofSeconds(6))
            coEvery { challengeRepository.getCurrentChallenge() } returns ApiResult.Error("network down")
            postCreationSignal.emit(PostCreatedEvent(UUID.randomUUID(), 1200L, 0, null))
            runCurrent()

            val state = viewModel.uiState.value
            assertTrue(state is ChallengeUiState.Active)
            state as ChallengeUiState.Active
            assertEquals(true, state.isStale)
            assertEquals(activeState.challengeId, state.challengeId)
            assertEquals(activeState.contributionCount, state.contributionCount)
        } finally {
            store.clear()
        }
    }

    // ---- Sincronizare dupa stergere/moderare (pas 4) ----

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `PostRemovedEvent in interiorul ferestrei de coalescing - refresh nu este suprimat`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, ChallengeProgress(3, RewardState.NONE)))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        val store = ViewModelStore().apply { put("challenge", viewModel) }
        try {
            runCurrent()
            coVerify(exactly = 1) { challengeRepository.getCurrentChallenge() }

            // No clock advance — still inside the 5s coalescing window. A removal must still
            // trigger a refresh (PullToRefresh bypass), unlike an ordinary `refresh()` call.
            coEvery { challengeRepository.getCurrentChallenge() } returns
                ApiResult.Success(CurrentChallenge(activeChallenge, ChallengeProgress(2, RewardState.NONE)))
            postRemovalSignal.emit(PostRemovedEvent(UUID.randomUUID(), PostRemovalReason.SelfDelete))
            runCurrent()

            coVerify(exactly = 2) { challengeRepository.getCurrentChallenge() }
            val state = viewModel.uiState.value as ChallengeUiState.Active
            assertEquals(2, state.contributionCount)
        } finally {
            store.clear()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `acelasi postId sters emis de doua ori - un singur refresh suplimentar`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val activeChallenge = challenge(startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600))
        coEvery { challengeRepository.getCurrentChallenge() } returns
            ApiResult.Success(CurrentChallenge(activeChallenge, ChallengeProgress(3, RewardState.NONE)))

        val viewModel = ChallengeViewModel(challengeRepository, clock, postCreationSignal, postRemovalSignal)
        val store = ViewModelStore().apply { put("challenge", viewModel) }
        try {
            runCurrent()
            coVerify(exactly = 1) { challengeRepository.getCurrentChallenge() }

            val removedPostId = UUID.randomUUID()
            postRemovalSignal.emit(PostRemovedEvent(removedPostId, PostRemovalReason.Moderation))
            runCurrent()
            coVerify(exactly = 2) { challengeRepository.getCurrentChallenge() }

            postRemovalSignal.emit(PostRemovedEvent(removedPostId, PostRemovalReason.Moderation))
            runCurrent()

            coVerify(exactly = 2) { challengeRepository.getCurrentChallenge() }
        } finally {
            store.clear()
        }
    }
}

/**
 * Test-only [Clock] whose instant can be advanced mid-test, to cross the refresh coalescing
 * window or simulate minutes passing for the ticker. [instantCallCount] lets a test prove the
 * ticker never read the clock at all (i.e. never started) rather than just happening to produce
 * an unchanged, already-deduplicated result.
 */
private class MutableClock(
    private var current: Instant,
    private val fixedZone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    var instantCallCount: Int = 0
        private set

    override fun getZone(): ZoneId = fixedZone
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant {
        instantCallCount++
        return current
    }

    fun advanceBy(duration: Duration) {
        current = current.plus(duration)
    }
}
