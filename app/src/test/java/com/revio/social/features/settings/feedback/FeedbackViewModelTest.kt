package com.revio.social.features.settings.feedback

import androidx.lifecycle.SavedStateHandle
import com.revio.social.MainDispatcherRule
import com.revio.social.core.device.DeviceInfoProvider
import com.revio.social.core.device.DiagnosticsInfo
import com.revio.social.core.feedback.Analytics
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.ConfusionReason
import com.revio.social.data.model.FeedbackCategory
import com.revio.social.data.model.UserFeedbackPayload
import com.revio.social.data.repository.FeedbackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val feedbackRepository: FeedbackRepository = mockk()
    private val deviceInfoProvider: DeviceInfoProvider = mockk()
    private val analytics: Analytics = mockk(relaxed = true)

    private fun createViewModel(): FeedbackViewModel {
        every { deviceInfoProvider.collect() } returns DiagnosticsInfo(
            appVersion = "1.0",
            androidVersion = "14",
            deviceModel = "Pixel 9 Pro",
            connectionType = "wifi",
        )
        return FeedbackViewModel(feedbackRepository, deviceInfoProvider, analytics, SavedStateHandle())
    }

    // ---- canSubmit per category ----

    @Test
    fun `canSubmit is false for NOT_WORKING without a message`() = runTest {
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.NOT_WORKING))

        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit is true for NOT_WORKING with a message`() = runTest {
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.NOT_WORKING))
        vm.onAction(FeedbackAction.MessageChanged("It crashed"))

        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit is true for GENERAL with only rating and reason`() = runTest {
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.GENERAL))
        vm.onAction(FeedbackAction.RatingSelected(4))
        vm.onAction(FeedbackAction.QuickReasonSelected(ConfusionReason.OTHER))

        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit is false for GENERAL with only a rating and no reason`() = runTest {
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.GENERAL))
        vm.onAction(FeedbackAction.RatingSelected(4))

        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit is true for CONFUSING with a message`() = runTest {
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.CONFUSING))
        vm.onAction(FeedbackAction.MessageChanged("Didn't know what to do"))

        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit is true for FEATURE_IDEA with a message`() = runTest {
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.FEATURE_IDEA))
        vm.onAction(FeedbackAction.MessageChanged("Add dark mode"))

        assertTrue(vm.uiState.value.canSubmit)
    }

    // ---- category selection advances the step ----

    @Test
    fun `selecting a category advances from CategoryPicker to Form`() = runTest {
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.GENERAL))

        assertEquals(FeedbackStep.Form, vm.uiState.value.step)
        assertEquals(FeedbackCategory.GENERAL, vm.uiState.value.category)
    }

    // ---- error handling preserves form content ----

    @Test
    fun `a network error keeps the message and other fields intact`() = runTest {
        coEvery { feedbackRepository.submitUserFeedback(any()) } returns
            ApiResult.Error(message = "Network error", code = "network_unavailable")
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.NOT_WORKING))
        vm.onAction(FeedbackAction.MessageChanged("It crashed"))
        vm.onAction(FeedbackAction.AreaSelected(com.revio.social.data.model.FeedbackArea.FEED))

        vm.onAction(FeedbackAction.Submit)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("It crashed", state.message)
        assertEquals(com.revio.social.data.model.FeedbackArea.FEED, state.area)
        assertTrue(state.isOffline)
        assertFalse(state.isSubmitting)
        assertEquals("You're offline. Try again when you're connected.", state.errorMessage)
    }

    @Test
    fun `a non-network error sets errorMessage without isOffline`() = runTest {
        coEvery { feedbackRepository.submitUserFeedback(any()) } returns
            ApiResult.Error(message = "Invalid category", code = "validation_error")
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.GENERAL))
        vm.onAction(FeedbackAction.RatingSelected(3))
        vm.onAction(FeedbackAction.QuickReasonSelected(ConfusionReason.OTHER))

        vm.onAction(FeedbackAction.Submit)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isOffline)
        assertEquals("Invalid category", state.errorMessage)
    }

    // ---- double submit ----

    @Test
    fun `double submit only calls the repository once`() = runTest {
        // A real suspension point (delay) between the two onAction calls simulates a rapid
        // double-tap arriving while the first request is still in flight — the guard is
        // `isSubmitting`, which only exists once the coroutine has actually started.
        coEvery { feedbackRepository.submitUserFeedback(any()) } coAnswers {
            kotlinx.coroutines.delay(10)
            ApiResult.Success(Unit)
        }
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.NOT_WORKING))
        vm.onAction(FeedbackAction.MessageChanged("It crashed"))

        vm.onAction(FeedbackAction.Submit)
        vm.onAction(FeedbackAction.Submit)
        advanceUntilIdle()

        coVerify(exactly = 1) { feedbackRepository.submitUserFeedback(any()) }
    }

    @Test
    fun `successful submit advances to the Sent step`() = runTest {
        coEvery { feedbackRepository.submitUserFeedback(any()) } returns ApiResult.Success(Unit)
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.NOT_WORKING))
        vm.onAction(FeedbackAction.MessageChanged("It crashed"))

        vm.onAction(FeedbackAction.Submit)
        advanceUntilIdle()

        assertEquals(FeedbackStep.Sent, vm.uiState.value.step)
    }

    // ---- send another resets state and rotates clientFeedbackId ----

    @Test
    fun `send another resets the form and generates a new clientFeedbackId`() = runTest {
        coEvery { feedbackRepository.submitUserFeedback(any()) } returns ApiResult.Success(Unit)
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.NOT_WORKING))
        vm.onAction(FeedbackAction.MessageChanged("It crashed"))
        val firstClientFeedbackId = vm.uiState.value.clientFeedbackId

        vm.onAction(FeedbackAction.Submit)
        advanceUntilIdle()
        vm.onAction(FeedbackAction.SendAnother)

        val state = vm.uiState.value
        assertEquals(FeedbackStep.CategoryPicker, state.step)
        assertNull(state.category)
        assertEquals("", state.message)
        assertNotEquals(firstClientFeedbackId, state.clientFeedbackId)
    }

    // ---- includeDiagnostics gates DeviceInfoProvider ----

    @Test
    fun `includeDiagnostics false omits device fields from the payload`() = runTest {
        coEvery { feedbackRepository.submitUserFeedback(any()) } returns ApiResult.Success(Unit)
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.NOT_WORKING))
        vm.onAction(FeedbackAction.MessageChanged("It crashed"))
        vm.onAction(FeedbackAction.ToggleIncludeDiagnostics(false))

        vm.onAction(FeedbackAction.Submit)
        advanceUntilIdle()

        coVerify(exactly = 0) { deviceInfoProvider.collect() }
        coVerify(exactly = 1) {
            feedbackRepository.submitUserFeedback(
                match<UserFeedbackPayload> {
                    it.appVersion == null && it.androidVersion == null && it.deviceModel == null && it.connectionType == null
                },
            )
        }
    }

    @Test
    fun `includeDiagnostics true includes device fields from the payload`() = runTest {
        coEvery { feedbackRepository.submitUserFeedback(any()) } returns ApiResult.Success(Unit)
        val vm = createViewModel()
        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.NOT_WORKING))
        vm.onAction(FeedbackAction.MessageChanged("It crashed"))

        vm.onAction(FeedbackAction.Submit)
        advanceUntilIdle()

        coVerify(exactly = 1) { deviceInfoProvider.collect() }
        coVerify(exactly = 1) {
            feedbackRepository.submitUserFeedback(
                match<UserFeedbackPayload> { it.appVersion == "1.0" && it.connectionType == "wifi" },
            )
        }
    }

    // ---- analytics ----

    private class FakeAnalytics : Analytics {
        val events = mutableListOf<com.revio.social.core.feedback.FeedbackEvent>()
        override fun log(event: com.revio.social.core.feedback.FeedbackEvent) {
            events += event
        }
    }

    @Test
    fun `a full flow logs screen_opened, category_selected, send_pressed, sent in order`() = runTest {
        val fakeAnalytics = FakeAnalytics()
        coEvery { feedbackRepository.submitUserFeedback(any()) } returns ApiResult.Success(Unit)
        every { deviceInfoProvider.collect() } returns DiagnosticsInfo(
            appVersion = "1.0", androidVersion = "14", deviceModel = "Pixel 9 Pro", connectionType = "wifi",
        )
        val vm = FeedbackViewModel(feedbackRepository, deviceInfoProvider, fakeAnalytics, SavedStateHandle())
        val secretMessage = "It crashed while uploading a photo of my Porsche 911"

        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.NOT_WORKING))
        vm.onAction(FeedbackAction.MessageChanged(secretMessage))
        vm.onAction(FeedbackAction.Submit)
        advanceUntilIdle()

        val names = fakeAnalytics.events.map { it.name }
        assertEquals(
            listOf(
                com.revio.social.core.feedback.FeedbackEventName.SCREEN_OPENED,
                com.revio.social.core.feedback.FeedbackEventName.CATEGORY_SELECTED,
                com.revio.social.core.feedback.FeedbackEventName.MESSAGE_STARTED,
                com.revio.social.core.feedback.FeedbackEventName.SEND_PRESSED,
                com.revio.social.core.feedback.FeedbackEventName.SENT,
            ),
            names,
        )
        fakeAnalytics.events.forEach { event ->
            assertNotEquals(secretMessage, event.category)
            assertNotEquals(secretMessage, event.area)
            assertNotEquals(secretMessage, event.source)
            assertNotEquals(secretMessage, event.reason)
        }
    }

    @Test
    fun `send another logs another_started and resets messageStarted tracking`() = runTest {
        val fakeAnalytics = FakeAnalytics()
        coEvery { feedbackRepository.submitUserFeedback(any()) } returns ApiResult.Success(Unit)
        every { deviceInfoProvider.collect() } returns DiagnosticsInfo(
            appVersion = "1.0", androidVersion = "14", deviceModel = "Pixel 9 Pro", connectionType = "wifi",
        )
        val vm = FeedbackViewModel(feedbackRepository, deviceInfoProvider, fakeAnalytics, SavedStateHandle())

        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.NOT_WORKING))
        vm.onAction(FeedbackAction.MessageChanged("It crashed"))
        vm.onAction(FeedbackAction.Submit)
        advanceUntilIdle()
        vm.onAction(FeedbackAction.SendAnother)

        assertTrue(
            fakeAnalytics.events.any { it.name == com.revio.social.core.feedback.FeedbackEventName.ANOTHER_STARTED },
        )

        vm.onAction(FeedbackAction.SelectCategory(FeedbackCategory.GENERAL))
        vm.onAction(FeedbackAction.MessageChanged("Great app"))

        // One MESSAGE_STARTED from the first form, one from the fresh form after SendAnother —
        // the reset must allow it to fire again, not suppress it forever.
        assertEquals(
            2,
            fakeAnalytics.events.count { it.name == com.revio.social.core.feedback.FeedbackEventName.MESSAGE_STARTED },
        )
    }
}
