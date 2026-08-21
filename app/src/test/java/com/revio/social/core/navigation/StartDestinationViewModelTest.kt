package com.revio.social.core.navigation

import com.revio.social.MainDispatcherRule
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.overlay.AppOverlayCoordinator
import com.revio.social.core.tour.TourController
import com.revio.social.data.local.auth.AuthTokens
import com.revio.social.data.local.auth.TokenStore
import com.revio.social.data.local.preferences.TourStatus
import com.revio.social.data.local.preferences.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * StartDestinationViewModel decide pe ce ecran ajunge userul la deschiderea aplicației.
 * Un bug aici = userul ajunge pe ecranul greșit la app start.
 * 4 cazuri practice — atât.
 */
class StartDestinationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun prefsMock(
        onboardingDone: Boolean,
        token: String?,
        userId: UUID?,
        tourStatus: TourStatus = TourStatus.Completed,
    ): UserPreferences = mockk<UserPreferences>().apply {
        every { onboardingCompleted } returns flowOf(onboardingDone)
        every { authToken } returns flowOf(token)
        every { this@apply.userId } returns flowOf(userId)
        coEvery { this@apply.tourStatus(any()) } returns tourStatus
        coEvery { clearAuthData() } returns Unit
        coEvery { removeLegacyJwt() } returns Unit
        coEvery { setTourStatus(any(), any()) } returns Unit
    }

    private fun tokenStoreMock(tokens: AuthTokens?): TokenStore = mockk<TokenStore>().apply {
        every { read() } returns tokens
        every { clear() } returns Unit
    }

    @Test
    fun `onboarding nefacut - duce la Onboarding indiferent de token`() = runTest {
        val vm = StartDestinationViewModel(
            prefsMock(onboardingDone = false, token = "any", userId = UUID.randomUUID())
        )

        assertEquals(Screen.Onboarding.route, vm.startDestination.value)
    }

    @Test
    fun `onboarding facut, fara token - duce la Login`() = runTest {
        val vm = StartDestinationViewModel(
            prefsMock(onboardingDone = true, token = null, userId = null)
        )

        assertEquals(Screen.Auth.route, vm.startDestination.value)
    }

    @Test
    fun `token salvat dar userId null - curata auth data si duce la Auth`() = runTest {
        val prefs = prefsMock(onboardingDone = true, token = "jwt", userId = null)
        val vm = StartDestinationViewModel(prefs)

        assertEquals(Screen.Auth.route, vm.startDestination.value)
        coVerify(exactly = 1) { prefs.clearAuthData() }
    }

    @Test
    fun `token onboarding in TokenStore si userId null - curata sesiunea locala si duce la Auth`() = runTest {
        val prefs = prefsMock(onboardingDone = true, token = null, userId = null)
        val tokenStore = tokenStoreMock(AuthTokens(accessToken = "onboarding-jwt", refreshToken = "refresh"))
        val vm = StartDestinationViewModel(prefs, tokenStore)

        assertEquals(Screen.Auth.route, vm.startDestination.value)
        verify(exactly = 1) { tokenStore.clear() }
        coVerify(exactly = 1) { prefs.clearAuthData() }
    }

    @Test
    fun `token + userId salvate - duce la Feed`() = runTest {
        val vm = StartDestinationViewModel(
            prefsMock(onboardingDone = true, token = "jwt", userId = UUID.randomUUID())
        )

        assertEquals(Screen.Feed.route, vm.startDestination.value)
    }

    @Test
    fun `tourStatus Unknown + sesiune valida - marcheaza Completed si nu porneste turul`() = runTest {
        val userId = UUID.randomUUID()
        val prefs = prefsMock(
            onboardingDone = true,
            token = "jwt",
            userId = userId,
            tourStatus = TourStatus.Unknown,
        )
        val tourController = TourController(prefs, mockk<AppOverlayCoordinator>(relaxed = true))

        val vm = StartDestinationViewModel(prefs, tourController = tourController)

        assertEquals(Screen.Feed.route, vm.startDestination.value)
        coVerify(exactly = 1) { prefs.setTourStatus(userId, TourStatus.Completed) }
        assertNull(tourController.step.value)
    }

    @Test
    fun `tourStatus Unknown + fara sesiune valida - nu scrie tourStatus`() = runTest {
        val prefs = prefsMock(
            onboardingDone = true,
            token = null,
            userId = null,
            tourStatus = TourStatus.Unknown,
        )
        val tourController = TourController(prefs, mockk<AppOverlayCoordinator>(relaxed = true))

        val vm = StartDestinationViewModel(prefs, tourController = tourController)

        assertEquals(Screen.Auth.route, vm.startDestination.value)
        coVerify(exactly = 0) { prefs.setTourStatus(any(), any()) }
    }

    // ── pas 2.1 — ev. 1 (app_start) + ev. 2 (session_restore_result), cele 4 ramuri ──────────

    @Test
    fun `onboarding nefacut - app_start cu destination onboarding, fara session_restore_result`() = runTest {
        val analyticsClient = mockk<AnalyticsClient>(relaxed = true)

        StartDestinationViewModel(
            prefsMock(onboardingDone = false, token = "any", userId = UUID.randomUUID()),
            analyticsClient = analyticsClient,
        )

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "app_start",
                    params = mapOf("destination" to AnalyticsParamValue.StringValue("onboarding")),
                )
            )
        }
        verify(exactly = 0) { analyticsClient.log(match { it.name == "session_restore_result" }) }
    }

    @Test
    fun `fara token - session_restore_result failure no_token si app_start cu destination auth`() = runTest {
        val analyticsClient = mockk<AnalyticsClient>(relaxed = true)

        StartDestinationViewModel(
            prefsMock(onboardingDone = true, token = null, userId = null),
            analyticsClient = analyticsClient,
        )

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "session_restore_result",
                    params = mapOf(
                        "outcome" to AnalyticsParamValue.StringValue("failure"),
                        "failure_code" to AnalyticsParamValue.StringValue("no_token"),
                    ),
                )
            )
        }
        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "app_start",
                    params = mapOf("destination" to AnalyticsParamValue.StringValue("auth")),
                )
            )
        }
    }

    @Test
    fun `token dar userId null - session_restore_result failure missing_user_id`() = runTest {
        val analyticsClient = mockk<AnalyticsClient>(relaxed = true)
        val prefs = prefsMock(onboardingDone = true, token = "jwt", userId = null)

        StartDestinationViewModel(prefs, analyticsClient = analyticsClient)

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "session_restore_result",
                    params = mapOf(
                        "outcome" to AnalyticsParamValue.StringValue("failure"),
                        "failure_code" to AnalyticsParamValue.StringValue("missing_user_id"),
                    ),
                )
            )
        }
    }

    @Test
    fun `token + userId salvate - session_restore_result success si app_start cu destination feed`() = runTest {
        val analyticsClient = mockk<AnalyticsClient>(relaxed = true)

        StartDestinationViewModel(
            prefsMock(onboardingDone = true, token = "jwt", userId = UUID.randomUUID()),
            analyticsClient = analyticsClient,
        )

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "session_restore_result",
                    params = mapOf("outcome" to AnalyticsParamValue.StringValue("success")),
                )
            )
        }
        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "app_start",
                    params = mapOf("destination" to AnalyticsParamValue.StringValue("feed")),
                )
            )
        }
    }
}
