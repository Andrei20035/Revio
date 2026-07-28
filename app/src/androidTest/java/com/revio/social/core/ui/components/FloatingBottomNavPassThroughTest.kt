package com.revio.social.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Confirms the Pas 4 fix keeps its non-consuming pointer-input node's original purpose: a tap on
 * a gap of the pill (between two slots, not on any icon's touch target) must NOT reach content
 * rendered behind [FloatingBottomNav]. Presence of the node in the hit-test chain is enough for
 * this — it doesn't require calling `consume()`, which is what broke child clicks before the fix.
 */
@RunWith(AndroidJUnit4::class)
class FloatingBottomNavPassThroughTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tap_pe_golul_dintre_Home_si_Leaderboard_nu_ajunge_la_continutul_din_spate`() {
        var backgroundClicks = 0
        var slotBounds by mutableStateOf(emptyMap<NavSlot, Rect>())

        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Red)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { backgroundClicks++ },
                        ),
                )

                FloatingBottomNav(
                    selected = FeedNavItem.Home,
                    profilePictureUrl = null,
                    onHome = {},
                    onLeaderboard = {},
                    onPlus = {},
                    onActivity = {},
                    onProfile = {},
                    onSlotBounds = { slot, rect -> slotBounds = slotBounds + (slot to rect) },
                )
            }
        }
        composeTestRule.waitForIdle()

        val homeBounds = slotBounds.getValue(NavSlot.Home)
        val leaderboardBounds = slotBounds.getValue(NavSlot.Leaderboard)

        // Midpoint between the two slots' touch targets: clearly inside the pill (both slots sit
        // on the same row), but outside both of their clickable bounds — a real gap, not an icon.
        val gapPoint = Offset(
            x = (homeBounds.right + leaderboardBounds.left) / 2f,
            y = (homeBounds.top + homeBounds.bottom) / 2f,
        )
        assertTrue(
            "expected the midpoint to fall strictly between the two slots' bounds",
            gapPoint.x > homeBounds.right && gapPoint.x < leaderboardBounds.left,
        )

        composeTestRule.onRoot().performTouchInput {
            down(gapPoint)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "tap over the nav pill's gap area must not reach the background",
            0,
            backgroundClicks,
        )
    }
}
