package com.revio.social.core.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.HiltTestActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pas 6: the red activity dot's *wiring* through real navigation — [ActivityDotViewModel] hooked
 * into all four main-tab screens, [ActivityScreen]'s `LaunchedEffect(Unit)` primary trigger, and
 * the idempotent secondary trigger on `onActivity` when already on Activity. Same
 * real-screens-through-Hilt approach as [RevioNavigationBottomNavTest].
 *
 * This does not assert the dot's on/off *value* — [com.revio.social.core.activitydot.ActivityDotController]
 * is a real `@Singleton` here with no test seam to force `hasUnseenActivity` deterministically
 * (no fake [com.revio.social.data.repository.NotificationRepository] is wired into this Hilt test
 * graph, matching every other instrumented test in this suite). What's covered is that neither
 * trigger crashes or corrupts navigation — the actual regression surface Pasul 6 introduces.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RevioNavigationActivityDotTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        hiltRule.inject()
        composeTestRule.activity.runOnUiThread {
            navController = TestNavHostController(composeTestRule.activity).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
        }
        composeTestRule.setContent {
            RevioNavigation(navController = navController, startDestination = Screen.Feed.route)
        }
        composeTestRule.waitForIdle()
    }

    private fun tapNavSlot(contentDescription: String) {
        composeTestRule.onNodeWithContentDescription(contentDescription).performTouchInput {
            down(center)
            moveBy(Offset(1f, 1f))
            up()
        }
        composeTestRule.waitForIdle()
    }

    private fun assertCurrentRoute(expected: String) {
        assertEquals(expected, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun `entering_Activity_via_the_tab_does_not_crash_and_lands_on_Activity`() {
        tapNavSlot("Activity")

        assertCurrentRoute(Screen.Activity.route)
    }

    @Test
    fun `tapping_the_Activity_tab_while_already_on_Activity_does_not_crash_or_renavigate`() {
        tapNavSlot("Activity")
        assertCurrentRoute(Screen.Activity.route)

        // Secondary trigger: onActivity fires again with no destination change.
        tapNavSlot("Activity")

        assertCurrentRoute(Screen.Activity.route)
    }

    @Test
    fun `restoring_the_back_stack_into_Activity_via_Feed_does_not_crash`() {
        tapNavSlot("Activity")
        assertCurrentRoute(Screen.Activity.route)

        tapNavSlot("Home")
        assertCurrentRoute(Screen.Feed.route)

        // saveState/restoreState round trip — the primary LaunchedEffect(Unit) trigger re-fires
        // on this recomposition of the Activity destination.
        tapNavSlot("Activity")
        assertCurrentRoute(Screen.Activity.route)
    }

    @Test
    fun `tapping_the_bell_from_Activity_opens_Notices_and_back_returns_to_Activity_without_crashing`() {
        // Pas 8: the bell badge now reads from NoticesUnreadViewModel/NoticesUnreadController
        // instead of a per-screen NotificationsViewModel instance — this exercises that wiring
        // through real navigation, same no-crash guarantee as the Activity-tab tests above.
        tapNavSlot("Activity")
        assertCurrentRoute(Screen.Activity.route)

        composeTestRule.onNodeWithContentDescription("Notices").performClick()
        composeTestRule.waitForIdle()
        assertCurrentRoute(Screen.Notices.route)

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        assertCurrentRoute(Screen.Activity.route)
    }

    @Test
    fun `Feed_Leaderboard_and_Profile_still_compose_and_navigate_with_the_dot_parameter_wired_in`() {
        tapNavSlot("Leaderboard")
        assertCurrentRoute(Screen.Leaderboard.route)

        tapNavSlot("Home")
        assertCurrentRoute(Screen.Feed.route)

        tapNavSlot("Profile")
        assertCurrentRoute(Screen.Profile.route)
    }
}
