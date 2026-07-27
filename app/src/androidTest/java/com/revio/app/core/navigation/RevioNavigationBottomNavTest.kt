package com.revio.app.core.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.app.HiltTestActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pas 6: drives the real four main-tab screens (Feed, Leaderboard, Activity, Profile) through
 * [RevioNavigation], using raw down/move/up gestures on [com.revio.app.core.ui.components.FloatingBottomNav]
 * from each screen to each of its destinations. Uses the real `hiltViewModel()`-backed screens
 * (via Hilt test injection) rather than fakes, since the bug under test lives in how the nav bar
 * is composed inside each screen (e.g. Feed/Profile pass a `hazeState`, Activity/Leaderboard don't).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RevioNavigationBottomNavTest {

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
    fun `din_Feed_navigheaza_catre_Leaderboard_Activity_si_Profile`() {
        assertCurrentRoute(Screen.Feed.route)

        tapNavSlot("Leaderboard")
        assertCurrentRoute(Screen.Leaderboard.route)

        tapNavSlot("Home")
        assertCurrentRoute(Screen.Feed.route)

        tapNavSlot("Activity")
        assertCurrentRoute(Screen.Activity.route)

        tapNavSlot("Home")
        assertCurrentRoute(Screen.Feed.route)

        tapNavSlot("Profile")
        assertCurrentRoute(Screen.Profile.route)

        tapNavSlot("Home")
        assertCurrentRoute(Screen.Feed.route)
    }

    @Test
    fun `din_Leaderboard_navigheaza_catre_celelalte_trei_taburi`() {
        tapNavSlot("Leaderboard")
        assertCurrentRoute(Screen.Leaderboard.route)

        tapNavSlot("Home")
        assertCurrentRoute(Screen.Feed.route)

        tapNavSlot("Leaderboard")
        assertCurrentRoute(Screen.Leaderboard.route)

        tapNavSlot("Activity")
        assertCurrentRoute(Screen.Activity.route)

        tapNavSlot("Leaderboard")
        assertCurrentRoute(Screen.Leaderboard.route)

        tapNavSlot("Profile")
        assertCurrentRoute(Screen.Profile.route)
    }

    @Test
    fun `din_Activity_navigheaza_catre_celelalte_trei_taburi`() {
        tapNavSlot("Activity")
        assertCurrentRoute(Screen.Activity.route)

        tapNavSlot("Home")
        assertCurrentRoute(Screen.Feed.route)

        tapNavSlot("Activity")
        assertCurrentRoute(Screen.Activity.route)

        tapNavSlot("Leaderboard")
        assertCurrentRoute(Screen.Leaderboard.route)

        tapNavSlot("Activity")
        assertCurrentRoute(Screen.Activity.route)

        tapNavSlot("Profile")
        assertCurrentRoute(Screen.Profile.route)
    }

    @Test
    fun `din_Profile_navigheaza_catre_celelalte_trei_taburi`() {
        tapNavSlot("Profile")
        assertCurrentRoute(Screen.Profile.route)

        tapNavSlot("Home")
        assertCurrentRoute(Screen.Feed.route)

        tapNavSlot("Profile")
        assertCurrentRoute(Screen.Profile.route)

        tapNavSlot("Leaderboard")
        assertCurrentRoute(Screen.Leaderboard.route)

        tapNavSlot("Profile")
        assertCurrentRoute(Screen.Profile.route)

        tapNavSlot("Activity")
        assertCurrentRoute(Screen.Activity.route)
    }
}
