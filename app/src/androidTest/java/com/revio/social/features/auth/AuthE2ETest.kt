package com.revio.social.features.auth

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.HiltTestActivity
import com.revio.social.core.navigation.RevioNavigation
import com.revio.social.core.navigation.Screen
import com.revio.social.core.network.ApiResult
import com.revio.social.data.model.AuthProvider
import com.revio.social.data.repository.AuthRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Inject

/**
 * Pas 6.2 — E2E pe funnel-ul P0 "auth" (ev. 5,6,7 din pas 2.2a/2.2b): pornește ecranul real
 * [AuthScreen] (Hilt, fără fake-uri) peste serverul real deja pornit local și verifică
 * secvența completă register/login → navigare către profile customization.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AuthE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    lateinit var authRepository: AuthRepository

    private lateinit var navController: TestNavHostController

    private val strongPassword = "Sup3r\$ecret1"

    private fun uniqueEmail(): String = "e2e-${UUID.randomUUID()}@example.com"

    @Before
    fun setUp() {
        hiltRule.inject()
        composeTestRule.activity.runOnUiThread {
            navController = TestNavHostController(composeTestRule.activity).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
        }
        composeTestRule.setContent {
            RevioNavigation(navController = navController, startDestination = Screen.Auth.route)
        }
        composeTestRule.waitForIdle()
    }

    private fun currentRoute(): String? = navController.currentBackStackEntry?.destination?.route

    private fun setTextFields(vararg values: String) {
        val fields = composeTestRule.onAllNodes(hasSetTextAction())
        values.forEachIndexed { index, value -> fields[index].performTextInput(value) }
    }

    @Test
    fun inregistrare_cu_email_nou_navigheaza_catre_profile_customization() {
        composeTestRule.onNodeWithText("Sign Up").performClick()
        composeTestRule.waitUntil(timeoutMillis = 2_000) {
            composeTestRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 3
        }

        setTextFields(uniqueEmail(), strongPassword, strongPassword)
        composeTestRule.onNodeWithText("Sign Up").performClick()

        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            currentRoute()?.startsWith("profile_customization") == true
        }
        assertTrue(currentRoute()!!.startsWith("profile_customization"))
    }

    @Test
    fun login_cu_cont_existent_navigheaza_catre_profile_customization() {
        val email = uniqueEmail()
        runBlocking {
            val result = authRepository.register(
                email = email,
                password = strongPassword,
                googleIdToken = null,
                provider = AuthProvider.REGULAR,
            )
            assertTrue("seed register trebuia sa reuseasca: $result", result is ApiResult.Success)
        }

        // Login mode e default (isLoginMode = true) — niciun toggle necesar.
        setTextFields(email, strongPassword)
        composeTestRule.onNodeWithText("Log In").performClick()

        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            currentRoute()?.startsWith("profile_customization") == true
        }
        assertTrue(currentRoute()!!.startsWith("profile_customization"))
    }
}
