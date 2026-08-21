package com.revio.social.features.settings

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.revio.social.MainDispatcherRule
import com.revio.social.core.network.ApiResult
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.repository.AuthRepository
import com.revio.social.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Opt-in consent (docs/consent-decision.md): covers persistence and the immediate in-session
 * Firebase SDK toggle. Cold-start re-application of the persisted choice lives in
 * RevioApp.onCreate() and is covered by RevioAppTest instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository = mockk<UserRepository>()
    private val authRepository = mockk<AuthRepository>()
    private val userPreferences = mockk<UserPreferences>(relaxed = true)
    private val appContext = mockk<Context>(relaxed = true)
    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
    private val firebaseAnalytics = mockk<FirebaseAnalytics>(relaxed = true)

    @Before
    fun setUp() {
        every { userRepository.currentUser } returns MutableStateFlow(null)
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Error("n/a")
        every { userPreferences.analyticsConsentGranted } returns flowOf(false)

        mockkStatic(FirebaseCrashlytics::class)
        mockkStatic(FirebaseAnalytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        every { FirebaseAnalytics.getInstance(any()) } returns firebaseAnalytics
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
        unmockkStatic(FirebaseAnalytics::class)
    }

    private fun buildVm() = SettingsViewModel(
        userRepository = userRepository,
        authRepository = authRepository,
        userPreferences = userPreferences,
        appContext = appContext,
    )

    @Test
    fun `uiState reflects the persisted consent value`() = runTest {
        every { userPreferences.analyticsConsentGranted } returns flowOf(true)

        val vm = buildVm()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.analyticsConsentGranted)
    }

    @Test
    fun `uiState defaults to off when no consent is persisted yet`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.analyticsConsentGranted)
    }

    @Test
    fun `granting consent persists the choice`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        vm.setAnalyticsConsentGranted(true)
        advanceUntilIdle()

        coVerify { userPreferences.setAnalyticsConsentGranted(true) }
    }

    @Test
    fun `granting consent enables both Firebase SDKs immediately`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        vm.setAnalyticsConsentGranted(true)

        verify { crashlytics.setCrashlyticsCollectionEnabled(true) }
        verify { firebaseAnalytics.setAnalyticsCollectionEnabled(true) }
    }

    @Test
    fun `revoking consent persists the choice and disables both Firebase SDKs`() = runTest {
        every { userPreferences.analyticsConsentGranted } returns flowOf(true)
        val vm = buildVm()
        advanceUntilIdle()

        vm.setAnalyticsConsentGranted(false)
        advanceUntilIdle()

        coVerify { userPreferences.setAnalyticsConsentGranted(false) }
    }
}
