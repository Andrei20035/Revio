package com.revio.social.core.activitydot

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ActivityDotViewModelTest {

    @Test
    fun `hasUnseenActivity exposes the controller's own StateFlow`() {
        val controllerFlow = MutableStateFlow(true)
        val controller = mockk<ActivityDotController>().apply {
            every { hasUnseenActivity } returns controllerFlow
        }

        val viewModel = ActivityDotViewModel(controller)

        assertSame(controllerFlow, viewModel.hasUnseenActivity)
        assertEquals(true, viewModel.hasUnseenActivity.value)
    }

    @Test
    fun `onActivityOpened delegates to the controller`() {
        val controller = mockk<ActivityDotController>(relaxed = true)
        val viewModel = ActivityDotViewModel(controller)

        viewModel.onActivityOpened()

        verify(exactly = 1) { controller.onActivityOpened() }
    }
}
