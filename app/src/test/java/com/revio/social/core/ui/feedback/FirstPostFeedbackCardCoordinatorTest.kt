package com.revio.social.core.ui.feedback

import com.revio.social.core.feedback.FeedbackEventName
import com.revio.social.core.feedback.FirstPostFeedbackController
import com.revio.social.core.feedback.FirstPostPromptState
import com.revio.social.core.feedback.PostCreatedEvent
import com.revio.social.core.network.ApiResult
import com.revio.social.core.notifications.NotificationPrepromptController
import com.revio.social.data.model.FeedbackSurface
import com.revio.social.data.model.FirstPostFeedbackPayload
import com.revio.social.data.model.QuickReason
import com.revio.social.data.repository.FeedbackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Exercises [FirstPostFeedbackCardCoordinator] against mocked [FirstPostFeedbackController] and
 * [FeedbackRepository] — no Hilt. The coordinator's own scope runs on a real background
 * dispatcher (like [FirstPostFeedbackController]'s), so assertions on its fire-and-forget submit
 * calls use [coVerify]'s timeout instead of virtual time.
 */
class FirstPostFeedbackCardCoordinatorTest {

    private val controllerState = MutableStateFlow<FirstPostPromptState>(FirstPostPromptState.Hidden)
    private val controller: FirstPostFeedbackController = mockk(relaxed = true)
    private val feedbackRepository: FeedbackRepository = mockk(relaxed = true)
    private val notificationPrepromptController: NotificationPrepromptController = mockk(relaxed = true)

    @Before
    fun setUp() {
        controllerState.value = FirstPostPromptState.Hidden
        every { controller.state } returns controllerState
        // A relaxed mock would otherwise fabricate a non-null PostCreatedEvent for this nullable
        // property instead of returning null — explicit default matching "no post yet" so tests
        // that don't care about pas 2.5c's post metrics keep asserting a bare payload.
        every { controller.lastPostCreatedEvent } returns null
        coEvery { feedbackRepository.submit(any()) } returns ApiResult.Success(Unit)
        coEvery { notificationPrepromptController.hasPermissionBeenRequestedBefore() } returns false
    }

    private fun coordinator() = FirstPostFeedbackCardCoordinator(controller, feedbackRepository, notificationPrepromptController)

    private fun showCard(coordinator: FirstPostFeedbackCardCoordinator) {
        controllerState.value = FirstPostPromptState.Rating
        Thread.sleep(200) // let the coordinator's collector pick up the new controller state
        assertEquals(FirstPostFeedbackStep.Rating, coordinator.cardState.value?.step)
    }

    @Test
    fun `closing from the Rating step submits nothing and just dismisses`() {
        val coordinator = coordinator()
        showCard(coordinator)

        coordinator.onCloseX()
        Thread.sleep(300)

        assertNull(coordinator.cardState.value)
        coVerify(exactly = 0) { feedbackRepository.submit(any()) }
        verify(exactly = 1) { controller.onClosedX() }
        verify(exactly = 0) { controller.onSubmitted(any()) }
    }

    @Test
    fun `closing from the Reason step submits the rating alone`() {
        val coordinator = coordinator()
        showCard(coordinator)
        coordinator.onRatingSelected(4)

        coordinator.onCloseX()
        assertNull(coordinator.cardState.value)

        coVerify(timeout = 2000) {
            feedbackRepository.submit(
                FirstPostFeedbackPayload(
                    rating = 4,
                    quickReason = null,
                    comment = null,
                    surface = FeedbackSurface.FEED,
                ),
            )
        }
        verify(exactly = 1) { controller.onSubmitted(FeedbackEventName.CLOSED_X) }
        verify(exactly = 0) { controller.onClosedX() }
    }

    @Test
    fun `closing from the Comment step drops a partially typed comment`() {
        val coordinator = coordinator()
        showCard(coordinator)
        coordinator.onRatingSelected(5)
        coordinator.onReasonSelected(QuickReason.EASY_TO_USE)
        coordinator.onCommentChanged("this was gr")

        coordinator.onCloseX()
        assertNull(coordinator.cardState.value)

        coVerify(timeout = 2000) {
            feedbackRepository.submit(
                FirstPostFeedbackPayload(
                    rating = 5,
                    quickReason = QuickReason.EASY_TO_USE,
                    comment = null,
                    surface = FeedbackSurface.FEED,
                ),
            )
        }
        verify(exactly = 1) { controller.onSubmitted(FeedbackEventName.CLOSED_X) }
    }

    @Test
    fun `Not Now behaves like closing X for a partial selection`() {
        val coordinator = coordinator()
        showCard(coordinator)
        coordinator.onRatingSelected(2)
        coordinator.onReasonSelected(QuickReason.SOMETHING_BROKE)

        coordinator.onNotNow()
        assertNull(coordinator.cardState.value)

        coVerify(timeout = 2000) {
            feedbackRepository.submit(
                FirstPostFeedbackPayload(
                    rating = 2,
                    quickReason = QuickReason.SOMETHING_BROKE,
                    comment = null,
                    surface = FeedbackSurface.FEED,
                ),
            )
        }
        verify(exactly = 1) { controller.onSubmitted(FeedbackEventName.NOT_NOW) }
        verify(exactly = 0) { controller.onNotNow() }
    }

    @Test
    fun `a failed partial submit still closes the card without an error`() {
        coEvery { feedbackRepository.submit(any()) } returns ApiResult.Error("boom")
        val coordinator = coordinator()
        showCard(coordinator)
        coordinator.onRatingSelected(1)

        coordinator.onCloseX()

        assertNull(coordinator.cardState.value)
        coVerify(timeout = 2000) { feedbackRepository.submit(any()) }
        verify(exactly = 1) { controller.onSubmitted(FeedbackEventName.CLOSED_X) }
    }

    // ----------------------------------------------------------------------
    // pas 2.5c — uploadDurationMs/retryCount/lastErrorCode ajung în payload-ul de feedback
    // ----------------------------------------------------------------------

    @Test
    fun `payload-ul de feedback include metricile postarii care a armat prompt-ul`() {
        every { controller.lastPostCreatedEvent } returns
            PostCreatedEvent(UUID.randomUUID(), uploadDurationMs = 4200L, retryCount = 2, lastErrorCode = "VALIDATION_ERROR")
        val coordinator = coordinator()
        showCard(coordinator)
        coordinator.onRatingSelected(3)

        coordinator.onCloseX()

        coVerify(timeout = 2000) {
            feedbackRepository.submit(
                FirstPostFeedbackPayload(
                    rating = 3,
                    quickReason = null,
                    comment = null,
                    surface = FeedbackSurface.FEED,
                    uploadDurationMs = 4200,
                    hadRetries = true,
                    lastErrorCode = "VALIDATION_ERROR",
                ),
            )
        }
    }

    @Test
    fun `fara PostCreatedEvent - payload-ul de feedback ramane fara metrici de postare`() {
        every { controller.lastPostCreatedEvent } returns null
        val coordinator = coordinator()
        showCard(coordinator)
        coordinator.onRatingSelected(3)

        coordinator.onCloseX()

        coVerify(timeout = 2000) {
            feedbackRepository.submit(
                FirstPostFeedbackPayload(
                    rating = 3,
                    quickReason = null,
                    comment = null,
                    surface = FeedbackSurface.FEED,
                ),
            )
        }
    }

    // ----------------------------------------------------------------------
    // pas 0.4 — "Yes, notify me" acționează efectiv permisiunea/Settings, prin controllerul comun
    // ----------------------------------------------------------------------

    @Test
    fun `un submit reusit ajunge la NotificationsPrompt cu flagul precalculat din controllerul comun, fara sa piarda payload-ul`() {
        coEvery { notificationPrepromptController.hasPermissionBeenRequestedBefore() } returns true
        val coordinator = coordinator()
        showCard(coordinator)
        coordinator.onRatingSelected(5)
        coordinator.onReasonSelected(QuickReason.EASY_TO_USE)

        coordinator.onSkip()

        coVerify(timeout = 2000) {
            feedbackRepository.submit(
                FirstPostFeedbackPayload(
                    rating = 5,
                    quickReason = QuickReason.EASY_TO_USE,
                    comment = null,
                    surface = FeedbackSurface.FEED,
                ),
            )
        }
        Thread.sleep(50) // let the same coroutine's subsequent _cardState assignment land
        assertEquals(
            FirstPostFeedbackStep.NotificationsPrompt(permissionPreviouslyRequested = true),
            coordinator.cardState.value?.step,
        )
    }

    @Test
    fun `un submit reusit cu permisiunea niciodata ceruta duce la NotificationsPrompt cu flagul false`() {
        coEvery { notificationPrepromptController.hasPermissionBeenRequestedBefore() } returns false
        val coordinator = coordinator()
        showCard(coordinator)
        coordinator.onRatingSelected(4)
        coordinator.onReasonSelected(QuickReason.EASY_TO_USE)

        coordinator.onSkip()

        coVerify(timeout = 2000) { feedbackRepository.submit(any()) }
        Thread.sleep(50)
        assertEquals(
            FirstPostFeedbackStep.NotificationsPrompt(permissionPreviouslyRequested = false),
            coordinator.cardState.value?.step,
        )
    }

    @Test
    fun `onNotificationsPermissionResult delegheaza la bookkeeping-ul comun al NotificationPrepromptController`() {
        val coordinator = coordinator()

        coordinator.onNotificationsPermissionResult(true)

        verify(exactly = 1) { notificationPrepromptController.onPermissionRequested(true) }
    }

    @Test
    fun `logNotificationsSettingsOpened delegheaza la bookkeeping-ul comun al NotificationPrepromptController`() {
        val coordinator = coordinator()

        coordinator.logNotificationsSettingsOpened()

        verify(exactly = 1) { notificationPrepromptController.logSettingsOpened() }
    }

    @Test
    fun `retryCount zero devine hadRetries false, nu null`() {
        every { controller.lastPostCreatedEvent } returns
            PostCreatedEvent(UUID.randomUUID(), uploadDurationMs = 900L, retryCount = 0, lastErrorCode = null)
        val coordinator = coordinator()
        showCard(coordinator)
        coordinator.onRatingSelected(5)

        coordinator.onCloseX()

        coVerify(timeout = 2000) {
            feedbackRepository.submit(
                FirstPostFeedbackPayload(
                    rating = 5,
                    quickReason = null,
                    comment = null,
                    surface = FeedbackSurface.FEED,
                    uploadDurationMs = 900,
                    hadRetries = false,
                    lastErrorCode = null,
                ),
            )
        }
    }
}
