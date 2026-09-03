package com.revio.social.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `activityHasDot` coverage (revio audit plan, Pasul 5): the Activity slot must render a visually
 * different icon when the dot is on, `selected` must always win over the dot, and none of this
 * may shift [NavSlot.Activity]'s reported bounds — the guided tour's spotlight anchors to them.
 */
@RunWith(AndroidJUnit4::class)
class FloatingBottomNavDotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `activityHasDot_renders_a_visually_different_Activity_icon_when_the_tab_is_not_selected`() {
        composeTestRule.setContent {
            Column {
                FloatingBottomNav(
                    selected = FeedNavItem.Home,
                    profilePictureUrl = null,
                    onHome = {},
                    onLeaderboard = {},
                    onPlus = {},
                    onActivity = {},
                    onProfile = {},
                    activityHasDot = false,
                )
                FloatingBottomNav(
                    selected = FeedNavItem.Home,
                    profilePictureUrl = null,
                    onHome = {},
                    onLeaderboard = {},
                    onPlus = {},
                    onActivity = {},
                    onProfile = {},
                    activityHasDot = true,
                )
            }
        }
        composeTestRule.waitForIdle()

        val activityNodes = composeTestRule.onAllNodesWithContentDescription("Activity")
        val withoutDot = activityNodes[0].captureToImage().asAndroidBitmap()
        val withDot = activityNodes[1].captureToImage().asAndroidBitmap()

        assertFalse("expected the dot variant to render different pixels", withoutDot.sameAs(withDot))
    }

    @Test
    fun `selected_takes_priority_over_activityHasDot_on_the_Activity_tab`() {
        composeTestRule.setContent {
            Column {
                FloatingBottomNav(
                    selected = FeedNavItem.Activity,
                    profilePictureUrl = null,
                    onHome = {},
                    onLeaderboard = {},
                    onPlus = {},
                    onActivity = {},
                    onProfile = {},
                    activityHasDot = false,
                )
                FloatingBottomNav(
                    selected = FeedNavItem.Activity,
                    profilePictureUrl = null,
                    onHome = {},
                    onLeaderboard = {},
                    onPlus = {},
                    onActivity = {},
                    onProfile = {},
                    activityHasDot = true,
                )
            }
        }
        composeTestRule.waitForIdle()

        val activityNodes = composeTestRule.onAllNodesWithContentDescription("Activity")
        val selectedWithoutDot = activityNodes[0].captureToImage().asAndroidBitmap()
        val selectedWithDot = activityNodes[1].captureToImage().asAndroidBitmap()

        assertTrue(
            "the selected variant must render identically regardless of activityHasDot",
            selectedWithoutDot.sameAs(selectedWithDot),
        )
    }

    @Test
    fun `activityHasDot_does_not_change_NavSlot_Activitys_reported_bounds`() {
        val boundsWithoutDot = mutableMapOf<NavSlot, Rect>()
        val boundsWithDot = mutableMapOf<NavSlot, Rect>()

        composeTestRule.setContent {
            Column {
                FloatingBottomNav(
                    selected = FeedNavItem.Home,
                    profilePictureUrl = null,
                    onHome = {},
                    onLeaderboard = {},
                    onPlus = {},
                    onActivity = {},
                    onProfile = {},
                    onSlotBounds = { slot, rect -> boundsWithoutDot[slot] = rect },
                    activityHasDot = false,
                )
                FloatingBottomNav(
                    selected = FeedNavItem.Home,
                    profilePictureUrl = null,
                    onHome = {},
                    onLeaderboard = {},
                    onPlus = {},
                    onActivity = {},
                    onProfile = {},
                    onSlotBounds = { slot, rect -> boundsWithDot[slot] = rect },
                    activityHasDot = true,
                )
            }
        }
        composeTestRule.waitForIdle()

        val withoutDot = boundsWithoutDot.getValue(NavSlot.Activity)
        val withDot = boundsWithDot.getValue(NavSlot.Activity)
        // Same size and same left/top offset within their own nav bar — the dot never resizes or
        // shifts the touch target/spotlight anchor.
        assertEquals(withoutDot.width, withDot.width, 0f)
        assertEquals(withoutDot.height, withDot.height, 0f)
        assertEquals(withoutDot.left, withDot.left, 0f)
    }
}
