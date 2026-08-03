package com.revio.social.core.ui.feedback

import com.revio.social.core.feedback.FeedbackEventName
import com.revio.social.core.feedback.FirstPostFeedbackController
import com.revio.social.core.feedback.FirstPostPromptState
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.FeedbackSurface
import com.revio.social.data.model.FirstPostFeedbackPayload
import com.revio.social.data.model.QuickReason
import com.revio.social.data.repository.FeedbackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    @Before
    fun setUp() {
        controllerState.value = FirstPostPromptState.Hidden
        every { controller.state } returns controllerState
        coEvery { feedbackRepository.submit(any()) } returns ApiResult.Success(Unit)
    }

    private fun coordinator() = FirstPostFeedbackCardCoordinator(controller, feedbackRepository)

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
}
