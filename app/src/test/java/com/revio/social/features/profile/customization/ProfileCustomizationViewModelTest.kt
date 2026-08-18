package com.revio.social.features.profile.customization

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.revio.social.MainDispatcherRule
import com.revio.social.core.earlyspotter.EarlySpotterController
import com.revio.social.core.image.ImageCompressor
import com.revio.social.core.navigation.Screen
import com.revio.social.core.network.ApiResult
import com.revio.social.data.local.auth.TokenStore
import com.revio.social.data.local.preferences.TourStatus
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.remote.dto.user.CreateUserResponse
import com.revio.social.data.repository.CarModelRepository
import com.revio.social.data.repository.UserCarRepository
import com.revio.social.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class ProfileCustomizationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun stubLog() {
        // completeProfileSetup()'s success/error paths call android.util.Log.d, unavailable
        // (throws) in a plain JVM unit test — stub it so the real code path under test can run.
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun unstubLog() {
        unmockkStatic(Log::class)
    }

    private val userRepository: UserRepository = mockk()
    private val userCarRepository: UserCarRepository = mockk(relaxed = true)
    private val userPreferences: UserPreferences = mockk(relaxed = true)
    private val carModelRepository: CarModelRepository = mockk(relaxed = true)
    private val imageCompressor: ImageCompressor = mockk(relaxed = true)
    private val tokenStore: TokenStore = mockk(relaxed = true)
    private val earlySpotterController: EarlySpotterController = mockk(relaxed = true)

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        ProfileCustomizationViewModel(
            userRepository = userRepository,
            userCarRepository = userCarRepository,
            userPreferences = userPreferences,
            carModelRepository = carModelRepository,
            imageCompressor = imageCompressor,
            tokenStore = tokenStore,
            earlySpotterController = earlySpotterController,
            savedStateHandle = savedStateHandle,
        )

    // ---- SavedStateHandle waitlist prefill ----

    @Test
    fun `SavedStateHandle with suggestedUsername and status prefills uiState username`() {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                Screen.ProfileCustomization.ARG_SUGGESTED_USERNAME to "coolname",
                Screen.ProfileCustomization.ARG_SUGGESTED_USERNAME_STATUS to "AVAILABLE",
            )
        )

        val vm = viewModel(savedStateHandle)

        assertEquals("coolname", vm.uiState.value.username)
        assertEquals("coolname", vm.uiState.value.suggestedUsername)
    }

    @Test
    fun `SavedStateHandle without args leaves uiState username blank without crashing`() {
        val vm = viewModel(SavedStateHandle())

        assertEquals("", vm.uiState.value.username)
    }

    // ---- completeProfileSetup: Early Spotter onProfileCreated + unconditional tour arming ----

    @Test
    fun `completeProfileSetup with isEarlySpotter true calls onProfileCreated with the correct args and arms the tour`() = runTest {
        val userId = UUID.randomUUID()
        every { userPreferences.userId } returns flowOf(null)
        coEvery { userRepository.createUser(any()) } returns ApiResult.Success(
            CreateUserResponse(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                userId = userId,
                isEarlySpotter = true,
                earlySpotterNumber = 7,
                earlySpotterBonusPoints = 300,
            )
        )

        val vm = viewModel()
        vm.updateFullName("Alice")
        vm.updateUsername("alice")
        vm.updateBirthDate(LocalDate.of(1995, 1, 1))
        vm.updateCountry("RO")

        vm.completeProfileSetup()

        // completeProfileSetup() launches on viewModelScope (its own dispatcher/scheduler, not
        // this runTest's), so a plain coVerify can race it — same reason TourControllerTest uses
        // a timeout on completeAndPersist's coVerify.
        coVerify(timeout = 1000) {
            earlySpotterController.onProfileCreated(
                isEarlySpotter = true,
                earlySpotterNumber = 7,
                bonusPoints = 300,
            )
        }
        coVerify(timeout = 1000) { userPreferences.setTourStatus(userId, TourStatus.Armed) }
        assertTrue(vm.uiState.value.isUserCreated)
    }
}
