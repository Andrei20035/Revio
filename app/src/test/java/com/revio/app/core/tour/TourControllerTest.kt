package com.revio.app.core.tour

import app.cash.turbine.test
import com.revio.app.MainDispatcherRule
import com.revio.app.data.local.preferences.TourStatus
import com.revio.app.data.local.preferences.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class TourControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun prefsMock(tourStatus: TourStatus): UserPreferences = mockk<UserPreferences>().apply {
        every { this@apply.tourStatus } returns flowOf(tourStatus)
        coEvery { setTourStatus(any()) } returns Unit
    }

    @Test
    fun `startIfArmed with Armed status starts the tour on Feed`() = runTest {
        val controller = TourController(prefsMock(TourStatus.Armed))

        controller.startIfArmed()

        assertEquals(TourStep.Feed, controller.step.value)
    }

    @Test
    fun `startIfArmed with Completed status does not start the tour`() = runTest {
        val controller = TourController(prefsMock(TourStatus.Completed))

        controller.startIfArmed()

        assertNull(controller.step.value)
    }

    @Test
    fun `advance chain visits all five steps in order`() = runTest {
        val controller = TourController(prefsMock(TourStatus.Armed))

        controller.step.test {
            assertNull(awaitItem())
            controller.startIfArmed()
            assertEquals(TourStep.Feed, awaitItem())
            controller.advance()
            assertEquals(TourStep.Leaderboard, awaitItem())
            controller.advance()
            assertEquals(TourStep.Activity, awaitItem())
            controller.advance()
            assertEquals(TourStep.Profile, awaitItem())
            controller.advance()
            assertEquals(TourStep.PostCta, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `advance is a no-op past the last step`() = runTest {
        val controller = TourController(prefsMock(TourStatus.Armed))
        controller.startIfArmed()
        repeat(4) { controller.advance() }
        assertEquals(TourStep.PostCta, controller.step.value)

        controller.advance()

        assertEquals(TourStep.PostCta, controller.step.value)
    }

    @Test
    fun `advance is a no-op when no tour is running`() = runTest {
        val controller = TourController(prefsMock(TourStatus.Completed))
        controller.startIfArmed()
        assertNull(controller.step.value)

        controller.advance()

        assertNull(controller.step.value)
    }

    @Test
    fun `completeAndPersist clears the step and persists Completed`() = runTest {
        val prefs = prefsMock(TourStatus.Armed)
        val controller = TourController(prefs)
        controller.startIfArmed()

        controller.completeAndPersist()

        assertNull(controller.step.value)
        coVerify(timeout = 1000) { prefs.setTourStatus(TourStatus.Completed) }
    }

    @Test
    fun `cancelForSessionLoss clears the step without persisting`() = runTest {
        val prefs = prefsMock(TourStatus.Armed)
        val controller = TourController(prefs)
        controller.startIfArmed()

        controller.cancelForSessionLoss()

        assertNull(controller.step.value)
        coVerify(exactly = 0) { prefs.setTourStatus(any()) }
    }
}
