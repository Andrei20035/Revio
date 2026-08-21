package com.revio.social.features.profile.customization

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.revio.social.MainDispatcherRule
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
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
import io.mockk.verify
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

    private val crashlytics: FirebaseCrashlytics = mockk(relaxed = true)

    @Before
    fun stubLog() {
        // completeProfileSetup()'s success/error paths call android.util.Log.d, unavailable
        // (throws) in a plain JVM unit test — stub it so the real code path under test can run.
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
    }

    @After
    fun unstubLog() {
        unmockkStatic(Log::class)
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private val userRepository: UserRepository = mockk()
    private val userCarRepository: UserCarRepository = mockk(relaxed = true)
    private val userPreferences: UserPreferences = mockk(relaxed = true)
    private val carModelRepository: CarModelRepository = mockk(relaxed = true)
    private val imageCompressor: ImageCompressor = mockk(relaxed = true)
    private val tokenStore: TokenStore = mockk(relaxed = true)
    private val earlySpotterController: EarlySpotterController = mockk(relaxed = true)
    private val analyticsClient: AnalyticsClient = mockk(relaxed = true)

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
            analyticsClient = analyticsClient,
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

    // ----------------------------------------------------------------------
    // pas 2.3a — ev. 10: onb_step_view
    // ----------------------------------------------------------------------

    @Test
    fun `creare ViewModel - onb_step_view cu step personal`() {
        viewModel()

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "onb_step_view",
                    params = mapOf("step" to AnalyticsParamValue.StringValue("personal")),
                )
            )
        }
    }

    @Test
    fun `previousStep pe pasul Personal - nu refireaza onb_step_view`() {
        val vm = viewModel()

        vm.previousStep()

        // O singură apariție — cea de la creare; pasul Personal e deja curent, deci previousStep
        // e no-op aici (nu există tranziție reală de step de logat).
        verify(exactly = 1) { analyticsClient.log(match { it.name == "onb_step_view" }) }
    }

    // ----------------------------------------------------------------------
    // pas 2.3b — ev. 11: onb_stage_result pe cele 7 etape din completeProfileSetup
    // ----------------------------------------------------------------------

    private fun stageResultEvent(stage: String, outcome: String) = AnalyticsEvent(
        name = "onb_stage_result",
        params = mapOf(
            "stage" to AnalyticsParamValue.StringValue(stage),
            "outcome" to AnalyticsParamValue.StringValue(outcome),
        ),
    )

    @Test
    fun `car_info_validation esueaza - onb_stage_result failure, restul etapelor nu ruleaza`() = runTest {
        val vm = viewModel()
        // Poză de mașină setată fără brand/model → isCarInfoValid() respinge combinația.
        vm.updateCarImage(ImageSource.Local(mockk<Uri>()))

        vm.completeProfileSetup()

        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("car_info_validation", "failure")) }
        verify(exactly = 0) { analyticsClient.log(match { it.name == "onb_stage_result" && it.params["stage"] != AnalyticsParamValue.StringValue("car_info_validation") }) }
    }

    @Test
    fun `user_profile esueaza - createUser respinge fara userId local existent`() = runTest {
        every { userPreferences.userId } returns flowOf(null)
        coEvery { userRepository.createUser(any()) } returns ApiResult.Error("Server error")

        val vm = viewModel()
        // birthDate e forțat non-null în createUserProfile() (`!!`) — fără el ajungem în
        // catch(Exception)/unexpected_error, nu în ramura de eroare gracioasă testată aici.
        vm.updateBirthDate(LocalDate.of(1995, 1, 1))
        vm.completeProfileSetup()

        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("car_info_validation", "success")) }
        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("user_profile", "failure")) }
    }

    @Test
    fun `profile_image_upload esueaza - uploadProfilePicture respinge`() = runTest {
        val userId = UUID.randomUUID()
        every { userPreferences.userId } returns flowOf(userId)
        coEvery { userRepository.uploadProfilePicture(any(), any()) } returns ApiResult.Error("Upload failed")

        val vm = viewModel()
        vm.updateProfileImage(ImageSource.Local(mockk<Uri>()))
        vm.completeProfileSetup()

        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("user_profile", "success")) }
        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("profile_image_upload", "failure")) }
    }

    @Test
    fun `user_car esueaza - poza de masina remote, nu Local, desi restul e valid`() = runTest {
        val userId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()
        every { userPreferences.userId } returns flowOf(userId)
        coEvery { carModelRepository.getModelsForBrand("Toyota") } returns
            ApiResult.Success(listOf(com.revio.social.data.remote.dto.car_model.CarModelOption(id = carModelId, model = "Corolla")))

        val vm = viewModel()
        // brand+model+carModelId valide (trec de isCarInfoValid) — dar poza e Remote, nu Local,
        // deci createUserCarIfNeeded() respinge separat, la propria sa verificare de tip.
        vm.updateCarImage(ImageSource.Remote("https://example.com/car.jpg"))
        vm.updateCarBrand("Toyota")
        vm.updateCarModel("Corolla")
        vm.completeProfileSetup()

        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("profile_image_upload", "success")) }
        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("user_car", "failure")) }
    }

    @Test
    fun `unexpected_error - setTourStatus arunca, restul etapelor de succes tot ruleaza inainte`() = runTest {
        val userId = UUID.randomUUID()
        every { userPreferences.userId } returns flowOf(userId)
        coEvery { userPreferences.setTourStatus(userId, TourStatus.Armed) } throws RuntimeException("boom")

        val vm = viewModel()
        vm.completeProfileSetup()

        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("user_car", "success")) }
        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("unexpected_error", "failure")) }
        // Etapele care depindeau de setTourStatus reușind nu apar — excepția a întrerupt fluxul înainte.
        verify(exactly = 0) { analyticsClient.log(stageResultEvent("tour_arm", "success")) }
        verify(exactly = 0) { analyticsClient.log(stageResultEvent("completed", "success")) }
    }

    @Test
    fun `toate cele 7 etape reusesc - onb_stage_result success in ordine`() = runTest {
        val userId = UUID.randomUUID()
        every { userPreferences.userId } returns flowOf(userId)

        val vm = viewModel()
        vm.completeProfileSetup()

        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("car_info_validation", "success")) }
        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("user_profile", "success")) }
        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("profile_image_upload", "success")) }
        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("user_car", "success")) }
        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("tour_arm", "success")) }
        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("completed", "success")) }
        verify(exactly = 0) { analyticsClient.log(match { it.name == "onb_stage_result" && it.params["outcome"] == AnalyticsParamValue.StringValue("failure") }) }
    }

    // ----------------------------------------------------------------------
    // pas 2.3c — ev. 12 (onb_completed) + ev. 13 (onb_abandoned_after_commit) + non-fatal
    // ----------------------------------------------------------------------

    @Test
    fun `esec la user_car dupa commit - onb_abandoned_after_commit + non-fatal o singura data`() = runTest {
        val userId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()
        every { userPreferences.userId } returns flowOf(userId)
        coEvery { carModelRepository.getModelsForBrand("Toyota") } returns
            ApiResult.Success(listOf(com.revio.social.data.remote.dto.car_model.CarModelOption(id = carModelId, model = "Corolla")))

        val vm = viewModel()
        vm.updateCarImage(ImageSource.Remote("https://example.com/car.jpg"))
        vm.updateCarBrand("Toyota")
        vm.updateCarModel("Corolla")
        vm.completeProfileSetup()

        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("user_car", "failure")) }
        verify(timeout = 1000) { analyticsClient.log(match { it.name == "onb_abandoned_after_commit" }) }
        verify(exactly = 1) { crashlytics.recordException(any()) }
        verify(exactly = 0) { analyticsClient.log(match { it.name == "onb_completed" }) }
    }

    @Test
    fun `exceptie neasteptata dupa commit - onb_abandoned_after_commit + non-fatal o singura data`() = runTest {
        val userId = UUID.randomUUID()
        every { userPreferences.userId } returns flowOf(userId)
        coEvery { userPreferences.setTourStatus(userId, TourStatus.Armed) } throws RuntimeException("boom")

        val vm = viewModel()
        vm.completeProfileSetup()

        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("unexpected_error", "failure")) }
        verify(timeout = 1000) { analyticsClient.log(match { it.name == "onb_abandoned_after_commit" }) }
        verify(exactly = 1) { crashlytics.recordException(any()) }
        verify(exactly = 0) { analyticsClient.log(match { it.name == "onb_completed" }) }
    }

    @Test
    fun `toate etapele reusesc - onb_completed apare, onb_abandoned_after_commit nu`() = runTest {
        val userId = UUID.randomUUID()
        every { userPreferences.userId } returns flowOf(userId)

        val vm = viewModel()
        vm.completeProfileSetup()

        verify(timeout = 1000) { analyticsClient.log(match { it.name == "onb_completed" }) }
        verify(exactly = 0) { analyticsClient.log(match { it.name == "onb_abandoned_after_commit" }) }
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `esec inainte de commit - onb_abandoned_after_commit nu apare`() = runTest {
        every { userPreferences.userId } returns flowOf(null)
        coEvery { userRepository.createUser(any()) } returns ApiResult.Error("Server error")

        val vm = viewModel()
        vm.updateBirthDate(LocalDate.of(1995, 1, 1))
        vm.completeProfileSetup()

        verify(timeout = 1000) { analyticsClient.log(stageResultEvent("user_profile", "failure")) }
        verify(exactly = 0) { analyticsClient.log(match { it.name == "onb_abandoned_after_commit" }) }
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }
}
