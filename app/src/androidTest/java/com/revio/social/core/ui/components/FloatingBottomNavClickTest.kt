package com.revio.social.core.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Characterization test: does a semantic click on each of the five nav slots invoke exactly
 * that slot's callback, and no other? This establishes the baseline behavior via
 * `performClick()` (semantic action), which bypasses the raw pointer-input pipeline entirely —
 * see [FloatingBottomNavBoundsTest] for bounds-only coverage.
 */
@RunWith(AndroidJUnit4::class)
class FloatingBottomNavClickTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private data class Counters(
        var home: Int = 0,
        var leaderboard: Int = 0,
        var plus: Int = 0,
        var activity: Int = 0,
        var profile: Int = 0,
    )

    private fun setContentWithCounters(): Counters {
        val counters = Counters()
        composeTestRule.setContent {
            FloatingBottomNav(
                selected = FeedNavItem.Home,
                profilePictureUrl = null,
                onHome = { counters.home++ },
                onLeaderboard = { counters.leaderboard++ },
                onPlus = { counters.plus++ },
                onActivity = { counters.activity++ },
                onProfile = { counters.profile++ },
            )
        }
        composeTestRule.waitForIdle()
        return counters
    }

    @Test
    fun `click_pe_Home_incrementeaza_doar_contorul_Home`() {
        val counters = setContentWithCounters()

        composeTestRule.onNodeWithContentDescription("Home").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, counters.home)
        assertEquals(0, counters.leaderboard)
        assertEquals(0, counters.plus)
        assertEquals(0, counters.activity)
        assertEquals(0, counters.profile)
    }

    @Test
    fun `click_pe_Leaderboard_incrementeaza_doar_contorul_Leaderboard`() {
        val counters = setContentWithCounters()

        composeTestRule.onNodeWithContentDescription("Leaderboard").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, counters.home)
        assertEquals(1, counters.leaderboard)
        assertEquals(0, counters.plus)
        assertEquals(0, counters.activity)
        assertEquals(0, counters.profile)
    }

    @Test
    fun `click_pe_Plus_incrementeaza_doar_contorul_Plus`() {
        val counters = setContentWithCounters()

        composeTestRule.onNodeWithContentDescription("Post your find").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, counters.home)
        assertEquals(0, counters.leaderboard)
        assertEquals(1, counters.plus)
        assertEquals(0, counters.activity)
        assertEquals(0, counters.profile)
    }

    @Test
    fun `click_pe_Activity_incrementeaza_doar_contorul_Activity`() {
        val counters = setContentWithCounters()

        composeTestRule.onNodeWithContentDescription("Activity").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, counters.home)
        assertEquals(0, counters.leaderboard)
        assertEquals(0, counters.plus)
        assertEquals(1, counters.activity)
        assertEquals(0, counters.profile)
    }

    @Test
    fun `click_pe_Profile_incrementeaza_doar_contorul_Profile`() {
        val counters = setContentWithCounters()

        composeTestRule.onNodeWithContentDescription("Profile").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, counters.home)
        assertEquals(0, counters.leaderboard)
        assertEquals(0, counters.plus)
        assertEquals(0, counters.activity)
        assertEquals(1, counters.profile)
    }

    /**
     * Raw down/move/up sequences, unlike `performClick()` above, exercise the actual pointer
     * pipeline (parent `pointerInput` consumption vs. child `clickable`'s
     * `waitForUpOrCancellation`). This is what should fail before the Pas 4 fix: any MOVE change
     * between DOWN and UP — even a sub-touch-slop jitter — gets cancelled by the parent's Main-pass
     * consumption, whereas a DOWN immediately followed by UP with no intervening MOVE succeeds.
     *
     * Each variant is its own @Test with its own `setContent` call: `ComposeContentTestRule`
     * only supports one `setContent` invocation per test method, so these cannot be combined into
     * one test that calls `setContentWithCounters()` repeatedly (that throws on the second call
     * and fails the whole test regardless of the gesture outcome).
     */
    private fun assertRawGestureClick(contentDescription: String, counterOf: (Counters) -> Int, gesture: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
        val counters = setContentWithCounters()
        composeTestRule.onNodeWithContentDescription(contentDescription).performTouchInput(gesture)
        composeTestRule.waitForIdle()
        assertEquals(1, counterOf(counters))
    }

    private val noMoveGesture: androidx.compose.ui.test.TouchInjectionScope.() -> Unit = {
        down(center)
        up()
    }
    private val tinyMoveGesture: androidx.compose.ui.test.TouchInjectionScope.() -> Unit = {
        down(center)
        moveBy(Offset(1f, 1f))
        up()
    }
    private val multipleMovesGesture: androidx.compose.ui.test.TouchInjectionScope.() -> Unit = {
        down(center)
        moveBy(Offset(1f, 0f))
        moveBy(Offset(0f, 1f))
        up()
    }

    @Test
    fun `Home_primeste_click_din_down_up_fara_move`() {
        assertRawGestureClick("Home", { it.home }, noMoveGesture)
    }

    @Test
    fun `Home_primeste_click_din_down_move_mica_up`() {
        assertRawGestureClick("Home", { it.home }, tinyMoveGesture)
    }

    @Test
    fun `Home_primeste_click_din_down_mai_multe_move_uri_up`() {
        assertRawGestureClick("Home", { it.home }, multipleMovesGesture)
    }

    @Test
    fun `Leaderboard_primeste_click_din_down_up_fara_move`() {
        assertRawGestureClick("Leaderboard", { it.leaderboard }, noMoveGesture)
    }

    @Test
    fun `Leaderboard_primeste_click_din_down_move_mica_up`() {
        assertRawGestureClick("Leaderboard", { it.leaderboard }, tinyMoveGesture)
    }

    @Test
    fun `Leaderboard_primeste_click_din_down_mai_multe_move_uri_up`() {
        assertRawGestureClick("Leaderboard", { it.leaderboard }, multipleMovesGesture)
    }

    @Test
    fun `Plus_primeste_click_din_down_up_fara_move`() {
        assertRawGestureClick("Post your find", { it.plus }, noMoveGesture)
    }

    @Test
    fun `Plus_primeste_click_din_down_move_mica_up`() {
        assertRawGestureClick("Post your find", { it.plus }, tinyMoveGesture)
    }

    @Test
    fun `Plus_primeste_click_din_down_mai_multe_move_uri_up`() {
        assertRawGestureClick("Post your find", { it.plus }, multipleMovesGesture)
    }

    @Test
    fun `Activity_primeste_click_din_down_up_fara_move`() {
        assertRawGestureClick("Activity", { it.activity }, noMoveGesture)
    }

    @Test
    fun `Activity_primeste_click_din_down_move_mica_up`() {
        assertRawGestureClick("Activity", { it.activity }, tinyMoveGesture)
    }

    @Test
    fun `Activity_primeste_click_din_down_mai_multe_move_uri_up`() {
        assertRawGestureClick("Activity", { it.activity }, multipleMovesGesture)
    }

    @Test
    fun `Profile_primeste_click_din_down_up_fara_move`() {
        assertRawGestureClick("Profile", { it.profile }, noMoveGesture)
    }

    @Test
    fun `Profile_primeste_click_din_down_move_mica_up`() {
        assertRawGestureClick("Profile", { it.profile }, tinyMoveGesture)
    }

    @Test
    fun `Profile_primeste_click_din_down_mai_multe_move_uri_up`() {
        assertRawGestureClick("Profile", { it.profile }, multipleMovesGesture)
    }
}
