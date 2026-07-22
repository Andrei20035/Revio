package com.revio.app.core.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class FloatingBottomNavBoundsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `toate cele cinci sloturi raporteaza bounds nevide, iar Plus e aproape de centrul barei`() {
        val bounds = mutableMapOf<NavSlot, Rect>()

        composeTestRule.setContent {
            FloatingBottomNav(
                selected = FeedNavItem.Home,
                profilePictureUrl = null,
                onHome = {},
                onLeaderboard = {},
                onPlus = {},
                onActivity = {},
                onProfile = {},
                onSlotBounds = { slot, rect -> bounds[slot] = rect },
            )
        }
        composeTestRule.waitForIdle()

        NavSlot.entries.forEach { slot ->
            val rect = bounds[slot]
            assertTrue("expected bounds reported for $slot", rect != null)
            assertTrue("expected non-empty bounds for $slot", rect!!.width > 0f && rect.height > 0f)
        }

        // "Horizontally centred" is relative to the nav bar's own row, not exact pixel-center:
        // the fixed 40dp NavIcon touch targets vs. the unwrapped, scaled Profile tab make the
        // row slightly asymmetric by construction, so a generous tolerance is used deliberately.
        val rowLeft = bounds.values.minOf { it.left }
        val rowRight = bounds.values.maxOf { it.right }
        val rowCenterX = (rowLeft + rowRight) / 2f
        val plusCenterX = bounds.getValue(NavSlot.Plus).center.x
        val tolerance = (rowRight - rowLeft) * 0.15f

        assertTrue(
            "Plus button should sit near the horizontal center of the nav bar " +
                "(plusCenterX=$plusCenterX, rowCenterX=$rowCenterX, tolerance=$tolerance)",
            abs(plusCenterX - rowCenterX) <= tolerance,
        )
    }
}
