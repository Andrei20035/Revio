package com.revio.social

import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.core.navigation.Screen
import com.revio.social.core.notifications.DeepLinkDestination
import com.revio.social.core.notifications.DeepLinkTarget
import com.revio.social.core.notifications.EXTRA_CHALLENGE_ID
import com.revio.social.core.notifications.EXTRA_DEEP_LINK
import com.revio.social.core.notifications.PendingDeepLink
import com.revio.social.data.local.auth.AuthTokens
import com.revio.social.data.local.auth.TokenStore
import com.revio.social.data.local.preferences.UserPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Push-notifications plan, "challenge is live" work — pas 7's instrumented coverage for
 * [RevioAppUI]'s deep-link-consuming effects, across the app states listed in the plan: cold
 * start, already running elsewhere (`onNewIntent`), logged out, and a double tap. Real Hilt graph
 * (no fakes) — [UserPreferences]/[TokenStore] are seeded directly with the login state each test
 * needs, the same real, offline-only state [StartDestinationViewModel] itself reads; no live
 * backend is required since none of that resolution makes a network call. A
 * [TestNavHostController] is injected into [RevioAppUI] (its `navController` param exists for
 * exactly this) so assertions read real nav state instead of matching on screen text.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RevioAppChallengeDeepLinkTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var tokenStore: TokenStore
    @Inject lateinit var pendingDeepLink: PendingDeepLink

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        hiltRule.inject()
        // Clean slate: a previous test's leftover buffered target or login state must not leak
        // into this one — all three are process-wide Hilt singletons.
        runBlocking {
            pendingDeepLink.consume()
            userPreferences.setOnboardingCompleted(true)
            userPreferences.clearAuthData()
            tokenStore.clear()
        }
    }

    private fun challengeIntent(challengeId: UUID): Intent = Intent().apply {
        putExtra(EXTRA_DEEP_LINK, "challenge")
        putExtra(EXTRA_CHALLENGE_ID, challengeId.toString())
    }

    /**
     * Captures [intent] on the main thread — mirrors [MainActivity]'s own
     * `lifecycleScope.launch { pendingDeepLink.capture(intent) }`. Calling the suspend function
     * from the instrumentation thread instead (plain `runBlocking`) races Navigation-Compose's
     * own main-thread-only lifecycle bookkeeping for the entries it's mutating and can crash the
     * app under test.
     */
    private fun captureOnMainThread(intent: Intent) {
        composeTestRule.runOnUiThread { runBlocking { pendingDeepLink.capture(intent) } }
    }

    private fun setDeepLinkOnMainThread(target: DeepLinkTarget) {
        composeTestRule.runOnUiThread { runBlocking { pendingDeepLink.set(target) } }
    }

    /** Seeds a real, locally-valid session — no network call is made to resolve `start`. */
    private fun logIn() {
        tokenStore.save(AuthTokens(accessToken = "access-${UUID.randomUUID()}", refreshToken = "refresh-${UUID.randomUUID()}"))
        runBlocking { userPreferences.saveUserId(UUID.randomUUID()) }
    }

    private fun setContent() {
        composeTestRule.activity.runOnUiThread {
            navController = TestNavHostController(composeTestRule.activity).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
        }
        composeTestRule.setContent {
            RevioAppUI(navController = navController)
        }
    }

    private fun waitForRoute(route: String, timeoutMillis: Long = 5_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            navController.currentDestination?.route == route
        }
    }

    @Test
    fun cold_start_with_a_buffered_challenge_deep_link_lands_on_ChallengeDetail_not_Feed() {
        logIn()
        val challengeId = UUID.randomUUID()
        captureOnMainThread(challengeIntent(challengeId))

        setContent()

        waitForRoute(Screen.ChallengeDetail.route)
        assertEquals(Screen.ChallengeDetail.route, navController.currentDestination?.route)
    }

    @Test
    fun feed_deep_link_still_lands_on_Feed_unchanged() {
        logIn()
        setDeepLinkOnMainThread(DeepLinkTarget(DeepLinkDestination.FEED))

        setContent()

        waitForRoute(Screen.Feed.route)
        assertEquals(Screen.Feed.route, navController.currentDestination?.route)
    }

    @Test
    fun app_open_elsewhere_plus_a_new_capture_navigates_to_ChallengeDetail() {
        logIn()
        setContent()
        waitForRoute(Screen.Feed.route)

        composeTestRule.runOnUiThread {
            navController.navigate(Screen.Leaderboard.route)
        }
        waitForRoute(Screen.Leaderboard.route)

        val challengeId = UUID.randomUUID()
        captureOnMainThread(challengeIntent(challengeId))

        waitForRoute(Screen.ChallengeDetail.route)
        assertEquals(Screen.ChallengeDetail.route, navController.currentDestination?.route)
    }

    @Test
    fun logged_out_a_buffered_challenge_deep_link_is_not_navigated_until_after_login() {
        // No logIn() call — tokenStore/userId are both cleared in setUp, so start resolves to Auth.
        val challengeId = UUID.randomUUID()
        captureOnMainThread(challengeIntent(challengeId))

        setContent()
        waitForRoute(Screen.Auth.route)
        assertEquals(Screen.Auth.route, navController.currentDestination?.route)

        // Stands in for a real login completing (AuthE2ETest already covers the login funnel
        // itself) — what matters here is only that arriving at Feed afterward is what lets the
        // still-buffered target through.
        composeTestRule.runOnUiThread {
            navController.navigate(Screen.Feed.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }

        waitForRoute(Screen.ChallengeDetail.route)
        assertEquals(Screen.ChallengeDetail.route, navController.currentDestination?.route)
    }

    @Test
    fun two_rapid_taps_on_the_same_challenge_produce_a_single_back_stack_entry() {
        logIn()
        setContent()
        waitForRoute(Screen.Feed.route)

        val challengeId = UUID.randomUUID()
        captureOnMainThread(challengeIntent(challengeId))
        waitForRoute(Screen.ChallengeDetail.route)
        captureOnMainThread(challengeIntent(challengeId))
        composeTestRule.waitForIdle()

        val entries = navController.currentBackStack.value.count { it.destination.route == Screen.ChallengeDetail.route }
        assertTrue("expected exactly one ChallengeDetail back-stack entry, found $entries", entries == 1)
    }
}
