package com.revio.social.core.feedback

import com.revio.social.core.network.ApiResult
import com.revio.social.core.overlay.ActiveOverlay
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.data.local.preferences.CachedPromptState
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.model.FIRST_POST_FEEDBACK_KEY
import com.revio.social.data.model.FeedbackPromptState
import com.revio.social.data.model.FeedbackSurface
import com.revio.social.data.model.PromptStatus
import com.revio.social.data.repository.FeedbackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Exercises [FirstPostFeedbackController] end to end against fakes — no Hilt, no real DataStore
 * (mirrors the [TourController] test's approach of mocking [UserPreferences] directly). The
 * controller schedules its reveal on a real background dispatcher (like [TourController]), so
 * tests that need the 2.5s delay to elapse use a short real wait instead of virtual time.
 */
class FirstPostFeedbackControllerTest {

    private val userId: UUID = UUID.randomUUID()
    private val postCreationSignal = PostCreationSignal()
    private val feedbackRepository: FeedbackRepository = mockk(relaxed = true)
    private val userPreferences: UserPreferences = mockk()
    private val overlayCoordinator: AppOverlayCoordinator = mockk()
    private val analytics: Analytics = mockk(relaxed = true)

    // Real flow so tests can switch the current user (drives the controller's user-changed reset).
    private val userIdFlow = MutableStateFlow<UUID?>(userId)

    // Stateful fakes for the two DataStore-backed pieces of state the controller reads and
    // writes across calls (arming, dismiss counts, cooldown timestamps), keyed per user so two
    // accounts can carry independent state within the same test.
    private val cachedStateByUser = mutableMapOf<UUID, CachedPromptState?>()
    private val armedByUser = mutableMapOf<UUID, Boolean>()

    private var cachedState: CachedPromptState?
        get() = cachedStateByUser[userId]
        set(value) {
            cachedStateByUser[userId] = value
        }

    private var armed: Boolean
        get() = armedByUser[userId] ?: false
        set(value) {
            armedByUser[userId] = value
        }

    @Before
    fun setUp() {
        cachedStateByUser.clear()
        armedByUser.clear()
        userIdFlow.value = userId

        every { userPreferences.userId } returns userIdFlow
        every { userPreferences.firstPostFeedbackState(any()) } answers {
            flowOf(cachedStateByUser[firstArg()])
        }
        every { userPreferences.firstPostFeedbackArmed(any()) } answers {
            flowOf(armedByUser[firstArg()] ?: false)
        }
        coEvery { userPreferences.setFirstPostFeedbackState(any(), any()) } answers {
            cachedStateByUser[firstArg()] = secondArg()
        }
        coEvery { userPreferences.setFirstPostFeedbackArmed(any(), any()) } answers {
            armedByUser[firstArg()] = secondArg()
        }

        every { overlayCoordinator.isBlockedBy(ActiveOverlay.FirstPostFeedback) } returns false
        every { overlayCoordinator.isBlockedByFlow(ActiveOverlay.FirstPostFeedback) } returns flowOf(false)
        every { overlayCoordinator.setActive(any(), any()) } returns Unit
        coEvery { feedbackRepository.getPromptState() } returns
            ApiResult.Success(FeedbackPromptState(FIRST_POST_FEEDBACK_KEY, PromptStatus.ELIGIBLE, 0, null))
    }

    private fun controller(clock: Clock = Clock.systemUTC()) = FirstPostFeedbackController(
        postCreationSignal,
        feedbackRepository,
        userPreferences,
        overlayCoordinator,
        analytics,
        clock,
    )

    private fun emitPostCreated() = runBlocking {
        postCreationSignal.emit(PostCreatedEvent(UUID.randomUUID(), 1200L, 0, null))
    }

    @Test
    fun `never arms or shows before a post-created event`() {
        val controller = controller()

        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        Thread.sleep(3500)

        assertEquals(FirstPostPromptState.Hidden, controller.state.value)
        coVerify(exactly = 0) { feedbackRepository.reportShown() }
        assertEquals(false, armed)
    }

    @Test
    fun `does not arm when server reports SUBMITTED or DISMISSED_TWICE`() {
        for (blockingStatus in listOf(PromptStatus.SUBMITTED, PromptStatus.DISMISSED_TWICE)) {
            armed = false
            coEvery { feedbackRepository.getPromptState() } returns
                ApiResult.Success(FeedbackPromptState(FIRST_POST_FEEDBACK_KEY, blockingStatus, 0, null))

            val controller = controller()
            emitPostCreated()
            Thread.sleep(300)

            assertEquals(false, armed)
            controller.onSurfaceReady(FeedbackSurface.FEED) { false }
            Thread.sleep(3500)
            assertEquals(FirstPostPromptState.Hidden, controller.state.value)
        }
    }

    @Test
    fun `shows at most once per session`() {
        armed = true
        val controller = controller()

        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        coVerify(timeout = 4000) { feedbackRepository.reportShown() }
        assertEquals(FirstPostPromptState.Rating, controller.state.value)

        // A second post-created event re-arms, but the session already showed once.
        armed = true
        controller.onSurfaceReady(FeedbackSurface.PROFILE) { false }
        Thread.sleep(3500)

        coVerify(exactly = 1) { feedbackRepository.reportShown() }
    }

    @Test
    fun `a blocker present when the delay elapses cancels the reveal`() {
        armed = true
        var blocked = false
        val controller = controller()

        controller.onSurfaceReady(FeedbackSurface.FEED) { blocked }
        blocked = true // a dialog opens while the 2.5s reveal delay is still running
        Thread.sleep(3500)

        assertEquals(FirstPostPromptState.Hidden, controller.state.value)
        coVerify(exactly = 0) { feedbackRepository.reportShown() }
    }

    @Test
    fun `cancelPendingShow logs abandoned navigation for an in-flight reveal`() {
        armed = true
        val controller = controller()

        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        Thread.sleep(200) // let the job start and enter its delay, but not finish it
        controller.cancelPendingShow()
        Thread.sleep(3500)

        assertEquals(FirstPostPromptState.Hidden, controller.state.value)
        coVerify(exactly = 0) { feedbackRepository.reportShown() }
        coVerify(exactly = 1) { analytics.log(FeedbackEvent(FeedbackEventName.ABANDONED_NAVIGATION)) }
    }

    @Test
    fun `cooldown reshow does not appear after only 2 days`() {
        assertCooldownReshow(daysAgo = 2, expectedToShow = false)
    }

    @Test
    fun `cooldown reshow appears after 4 days`() {
        assertCooldownReshow(daysAgo = 4, expectedToShow = true)
    }

    @Test
    fun `cooldown reshow does not appear after 6 days`() {
        assertCooldownReshow(daysAgo = 6, expectedToShow = false)
    }

    private fun assertCooldownReshow(daysAgo: Long, expectedToShow: Boolean) {
        val now = Instant.parse("2026-01-15T12:00:00Z")
        cachedState = CachedPromptState(
            status = PromptStatus.DISMISSED_ONCE,
            shownCount = 1,
            lastShownAt = now.minus(daysAgo, ChronoUnit.DAYS),
        )
        armed = false
        val controller = controller(clock = Clock.fixed(now, ZoneOffset.UTC))

        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        Thread.sleep(3500)

        val expectedState = if (expectedToShow) FirstPostPromptState.Rating else FirstPostPromptState.Hidden
        assertEquals(expectedState, controller.state.value)
    }

    @Test
    fun `never shows again after a second dismissal`() {
        var now = Instant.parse("2026-01-01T09:00:00Z")

        // Session 1: arms, shows, user dismisses with "Not now" -> DISMISSED_ONCE.
        armed = true
        var controller = controller(clock = Clock.fixed(now, ZoneOffset.UTC))
        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        coVerify(timeout = 4000) { feedbackRepository.reportShown() }
        controller.onNotNow()
        Thread.sleep(300)
        assertEquals(PromptStatus.DISMISSED_ONCE, cachedState?.status)

        // Session 2, 4 days later (within the single reshow window): shows again, dismissed again -> DISMISSED_TWICE.
        now = now.plus(4, ChronoUnit.DAYS)
        cachedState = cachedState?.copy(lastShownAt = now.minus(4, ChronoUnit.DAYS))
        controller = controller(clock = Clock.fixed(now, ZoneOffset.UTC))
        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        Thread.sleep(3500)
        assertEquals(FirstPostPromptState.Rating, controller.state.value)
        controller.onNotNow()
        Thread.sleep(300)
        assertEquals(PromptStatus.DISMISSED_TWICE, cachedState?.status)

        // Session 3, another 4 days later — still within a plausible reshow window, but the
        // budget of one automatic reshow is spent and the status is now DISMISSED_TWICE.
        now = now.plus(4, ChronoUnit.DAYS)
        cachedState = cachedState?.copy(lastShownAt = now.minus(4, ChronoUnit.DAYS))
        controller = controller(clock = Clock.fixed(now, ZoneOffset.UTC))
        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        Thread.sleep(3500)
        assertEquals(FirstPostPromptState.Hidden, controller.state.value)
    }

    @Test
    fun `two rapid posts result in exactly one show`() {
        val controller = controller()

        emitPostCreated()
        emitPostCreated()
        Thread.sleep(300)
        assertEquals(true, armed)

        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        Thread.sleep(3500)

        assertEquals(FirstPostPromptState.Rating, controller.state.value)
        coVerify(exactly = 1) { feedbackRepository.reportShown() }
    }

    @Test
    fun `prompt becomes eligible again for a new account in the same process`() {
        val userB = UUID.randomUUID()
        armedByUser[userId] = true
        val controller = controller()

        // Account A shows once, exhausting its session guard.
        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        coVerify(timeout = 4000) { feedbackRepository.reportShown() }
        assertEquals(FirstPostPromptState.Rating, controller.state.value)

        // Account deleted, new account B logged in, all within the same process.
        armedByUser[userB] = true
        userIdFlow.value = null
        userIdFlow.value = userB
        Thread.sleep(300) // let the user-changed reset collector run

        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        coVerify(timeout = 4000, exactly = 2) { feedbackRepository.reportShown() }
        assertEquals(FirstPostPromptState.Rating, controller.state.value)
    }

    @Test
    fun `same account still shows at most once per session after an unrelated user-change reset`() {
        armed = true
        val controller = controller()

        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        coVerify(timeout = 4000) { feedbackRepository.reportShown() }
        assertEquals(FirstPostPromptState.Rating, controller.state.value)

        // Re-emitting the same user id must not count as a user change (distinctUntilChanged).
        userIdFlow.value = userId
        Thread.sleep(300)

        armed = true
        controller.onSurfaceReady(FeedbackSurface.PROFILE) { false }
        Thread.sleep(3500)

        coVerify(exactly = 1) { feedbackRepository.reportShown() }
    }

    @Test
    fun `switching accounts cancels a pending reveal scheduled for the previous account`() {
        val userB = UUID.randomUUID()
        armed = true
        val controller = controller()

        controller.onSurfaceReady(FeedbackSurface.FEED) { false }
        Thread.sleep(200) // job is scheduled and mid-delay, but hasn't revealed yet

        armedByUser[userB] = false // B is not eligible yet
        userIdFlow.value = userB
        Thread.sleep(3500) // long enough for A's original delay to have elapsed

        assertEquals(FirstPostPromptState.Hidden, controller.state.value)
        coVerify(exactly = 0) { feedbackRepository.reportShown() }
    }
}
