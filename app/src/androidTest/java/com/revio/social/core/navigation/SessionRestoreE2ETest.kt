package com.revio.social.core.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.HiltTestActivity
import com.revio.social.data.local.auth.AuthTokens
import com.revio.social.data.local.auth.TokenStore
import com.revio.social.data.local.preferences.UserPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Inject

/**
 * Pas 6.2 — E2E pe funnel-ul P0 "app start & session restore" (ev. 1,2 din pas 2.1): pornește
 * [com.revio.social.core.navigation.StartDestinationViewModel] + [RevioNavigation] reale (Hilt,
 * fără fake-uri) peste cele 3 stări posibile de sesiune locală și verifică unde ajunge navigarea.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionRestoreE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var tokenStore: TokenStore

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            userPreferences.clearAuthData()
            userPreferences.resetOnboardingStatus()
        }
        tokenStore.clear()
    }

    @After
    fun tearDown() {
        runBlocking {
            userPreferences.clearAuthData()
            userPreferences.resetOnboardingStatus()
        }
        tokenStore.clear()
    }

    private fun launchAndAwaitDestination(): String {
        composeTestRule.activity.runOnUiThread {
            navController = TestNavHostController(composeTestRule.activity).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
        }
        composeTestRule.setContent {
            val startVm: StartDestinationViewModel = hiltViewModel()
            val start by startVm.startDestination.collectAsState()
            start?.let { RevioNavigation(navController = navController, startDestination = it) }
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            navController.currentBackStackEntry?.destination?.route != null
        }
        composeTestRule.waitForIdle()
        return navController.currentBackStackEntry?.destination?.route.orEmpty()
    }

    @Test
    fun onboarding_neterminat_navigheaza_catre_ecranul_de_onboarding() {
        // clearAuthData/resetOnboardingStatus (setUp) lasă onboardingCompleted = false, implicit.
        val route = launchAndAwaitDestination()

        assertEquals(Screen.Onboarding.route, route)
    }

    @Test
    fun onboarding_terminat_fara_sesiune_navigheaza_catre_auth() {
        runBlocking { userPreferences.setOnboardingCompleted(true) }
        // tokenStore rămâne gol (setUp) — nicio sesiune locală.

        val route = launchAndAwaitDestination()

        assertEquals(Screen.Auth.route, route)
    }

    @Test
    fun sesiune_locala_valida_navigheaza_catre_feed() {
        runBlocking {
            userPreferences.setOnboardingCompleted(true)
            userPreferences.saveUserId(UUID.randomUUID())
        }
        tokenStore.save(AuthTokens(accessToken = "e2e-access", refreshToken = "e2e-refresh"))

        val route = launchAndAwaitDestination()

        assertEquals(Screen.Feed.route, route)
    }
}
