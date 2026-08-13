package com.revio.social.core.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.HiltTestActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that `admin/challenges/create` and `admin/challenges/detail/{id}` are registered
 * destinations rendering their real screens, not
 * [com.revio.social.features.settings.PlaceholderScreen]. Also pins the navigation precondition
 * `AdminChallengeDetailScreen`'s post-publish/finalize dashboard-refresh flag relies on: from
 * either path that reaches the detail screen, `Screen.AdminChallenges.route` must still be
 * resolvable via `NavController.getBackStackEntry` (which throws if the route isn't on the stack).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RevioNavigationAdminChallengeRoutesTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var navController: TestNavHostController

    private fun setContent(startDestination: String) {
        hiltRule.inject()
        composeTestRule.activity.runOnUiThread {
            navController = TestNavHostController(composeTestRule.activity).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
        }
        composeTestRule.setContent {
            RevioNavigation(navController = navController, startDestination = startDestination)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun admin_challenge_create_route_randeaza_wizardul_real_nu_placeholder_ul() {
        setContent(Screen.AdminChallengeCreate.createRoute())

        composeTestRule.onNodeWithText("New challenge").assertExists()
        composeTestRule.onNodeWithText("Which cars count?").assertExists()
        composeTestRule.onNodeWithText("Coming soon").assertDoesNotExist()
    }

    @Test
    fun admin_challenge_detail_route_nu_randeaza_PlaceholderScreen() {
        setContent(Screen.AdminChallengeDetail.createRoute(UUID.randomUUID()))

        composeTestRule.onNodeWithText("Coming soon").assertDoesNotExist()
    }

    @Test
    fun navigarea_din_lista_catre_detaliu_pastreaza_admin_challenges_pe_back_stack() {
        setContent(Screen.AdminChallenges.route)

        composeTestRule.activity.runOnUiThread {
            navController.navigate(Screen.AdminChallengeDetail.createRoute(UUID.randomUUID()))
        }
        composeTestRule.waitForIdle()

        assertEquals(Screen.AdminChallenges.route, navController.previousBackStackEntry?.destination?.route)
        // getBackStackEntry throws IllegalArgumentException if the route isn't on the stack — this
        // is exactly what AdminChallengeDetailScreen relies on to set ADMIN_CHALLENGE_CHANGED_KEY
        // after a successful publish/finalize.
        assertNotNull(navController.getBackStackEntry(Screen.AdminChallenges.route))
    }

    @Test
    fun navigarea_de_la_wizard_catre_detaliu_pastreaza_admin_challenges_pe_back_stack() {
        setContent(Screen.AdminChallenges.route)

        composeTestRule.activity.runOnUiThread {
            navController.navigate(Screen.AdminChallengeCreate.createRoute())
        }
        composeTestRule.waitForIdle()

        composeTestRule.activity.runOnUiThread {
            navController.navigate(Screen.AdminChallengeDetail.createRoute(UUID.randomUUID())) {
                popUpTo(Screen.AdminChallengeCreate.route) { inclusive = true }
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(Screen.AdminChallenges.route, navController.previousBackStackEntry?.destination?.route)
        assertNotNull(navController.getBackStackEntry(Screen.AdminChallenges.route))
    }
}
