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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pas A1: asserts that `my_challenges` and `challenge/{id}` render their real destinations,
 * not [com.revio.social.features.settings.PlaceholderScreen]. Written before the routes are
 * fixed in [RevioNavigation], so it is expected to fail until A2/A3 land.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RevioNavigationChallengeRoutesTest {

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
    fun `my_challenges_route_nu_randeaza_PlaceholderScreen`() {
        setContent(Screen.MyChallenges.route)

        composeTestRule.onNodeWithText("Coming soon").assertDoesNotExist()
    }

    @Test
    fun `challenge_detail_route_nu_randeaza_PlaceholderScreen`() {
        setContent(Screen.ChallengeDetail.createRoute(UUID.randomUUID()))

        composeTestRule.onNodeWithText("Coming soon").assertDoesNotExist()
    }
}
