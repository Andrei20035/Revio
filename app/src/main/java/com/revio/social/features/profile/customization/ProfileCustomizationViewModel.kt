package com.revio.social.features.profile.customization

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.earlyspotter.EarlySpotterController
import com.revio.social.core.image.CropTransform
import com.revio.social.core.image.ImageCompressor
import com.revio.social.core.navigation.Screen
import com.revio.social.features.profile.components.ImageTransformState
import com.revio.social.data.local.preferences.TourStatus
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.local.auth.AuthTokens
import com.revio.social.data.local.auth.TokenStore
import com.revio.social.data.remote.dto.auth.WaitlistUsernameStatus
import com.revio.social.data.remote.dto.user.CreateUserRequest
import com.revio.social.data.remote.dto.user_car.UserCarRequest
import com.revio.social.core.network.ApiResult
import com.revio.social.data.repository.CarModelRepository
import com.revio.social.data.repository.UserCarRepository
import com.revio.social.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** ev. 10 — fired whenever a profile-customization step becomes visible. Fixed set: `personal`, `car`. */
private const val EVENT_ONB_STEP_VIEW = "onb_step_view"

/**
 * ev. 11 — fired at each of [completeProfileSetup]'s 7 stages, so a drop-off funnel shows
 * exactly where users get stuck. `stage` is a fixed, closed set (this event's own `stage`
 * parameter is the failure identifier — no separate `failure_code` on top of it):
 * `car_info_validation`, `user_profile`, `profile_image_upload`, `user_car`, `tour_arm`,
 * `completed` (success-only — the terminal marker), `unexpected_error` (failure-only — the
 * catch-all for any of the above throwing instead of returning a result).
 */
private const val EVENT_ONB_STAGE_RESULT = "onb_stage_result"
private const val STAGE_CAR_INFO_VALIDATION = "car_info_validation"
private const val STAGE_USER_PROFILE = "user_profile"
private const val STAGE_PROFILE_IMAGE_UPLOAD = "profile_image_upload"
private const val STAGE_USER_CAR = "user_car"
private const val STAGE_TOUR_ARM = "tour_arm"
private const val STAGE_COMPLETED = "completed"
private const val STAGE_UNEXPECTED_ERROR = "unexpected_error"

/** ev. 12 — fired once, when [completeProfileSetup] reaches its terminal success. */
private const val EVENT_ONB_COMPLETED = "onb_completed"

/**
 * ev. 13 — the account was already committed server-side (a userId was obtained — either
 * freshly via `POST /users` or reused from a prior attempt), but a later stage in
 * [completeProfileSetup] then failed, leaving the user with a created account and incomplete
 * onboarding (risc H8/G12 din plan — "cont creat, onboarding incomplet, nereparabil"). Also
 * records a non-fatal, once per attempt (each invocation takes exactly one exit path).
 */
private const val EVENT_ONB_ABANDONED_AFTER_COMMIT = "onb_abandoned_after_commit"

/** Reported to Crashlytics alongside [EVENT_ONB_ABANDONED_AFTER_COMMIT] — see pas 2.3c. */
private class OnboardingAbandonedAfterCommitException : Exception("onb_abandoned_after_commit")

@HiltViewModel
class ProfileCustomizationViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userCarRepository: UserCarRepository,
    private val userPreferences: UserPreferences,
    private val carModelRepository: CarModelRepository,
    private val imageCompressor: ImageCompressor,
    private val tokenStore: TokenStore,
    private val earlySpotterController: EarlySpotterController,
    savedStateHandle: SavedStateHandle,
    private val analyticsClient: AnalyticsClient? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileCustomizationUiState())
    val uiState: StateFlow<ProfileCustomizationUiState> = _uiState.asStateFlow()

    init {
        // Waitlist prefill carried across the nav boundary from AuthScreen — see
        // Screen.ProfileCustomization.createRoute(). Explicit here rather than as a
        // ProfileCustomizationUiState constructor default, which only applied once at object
        // construction and broke on any subsequent .copy().
        val suggestedUsername = savedStateHandle.get<String>(Screen.ProfileCustomization.ARG_SUGGESTED_USERNAME)
        val suggestedUsernameStatus = savedStateHandle
            .get<String>(Screen.ProfileCustomization.ARG_SUGGESTED_USERNAME_STATUS)
            ?.let { runCatching { WaitlistUsernameStatus.valueOf(it) }.getOrNull() }

        if (suggestedUsernameStatus != null) {
            _uiState.update {
                it.copy(
                    suggestedUsername = suggestedUsername,
                    suggestedUsernameStatus = suggestedUsernameStatus,
                    username = suggestedUsername ?: it.username,
                )
            }
        }

        logStepView(_uiState.value.currentStep)
    }

    private fun logStepView(step: ProfileStep) {
        val stepValue = when (step) {
            ProfileStep.Personal -> "personal"
            ProfileStep.Car -> "car"
        }
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_ONB_STEP_VIEW,
                params = mapOf("step" to AnalyticsParamValue.StringValue(stepValue)),
            )
        )
    }

    private fun logStageResult(stage: String, success: Boolean) {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_ONB_STAGE_RESULT,
                params = mapOf(
                    "stage" to AnalyticsParamValue.StringValue(stage),
                    "outcome" to AnalyticsParamValue.StringValue(if (success) "success" else "failure"),
                ),
            )
        )
    }

    private fun logOnboardingCompleted() {
        analyticsClient?.log(AnalyticsEvent(name = EVENT_ONB_COMPLETED))
    }

    /** No-op when [committedUserId] is null — nothing was committed yet, so nothing to abandon. */
    private fun logAbandonedAfterCommit(committedUserId: UUID?) {
        if (committedUserId == null) return
        analyticsClient?.log(AnalyticsEvent(name = EVENT_ONB_ABANDONED_AFTER_COMMIT))
        try {
            FirebaseCrashlytics.getInstance().recordException(OnboardingAbandonedAfterCommitException())
        } catch (_: Exception) {
            // Reporting must never break the real call path (e.g. Firebase not initialized in tests).
        }
    }

    fun updateProfileImage(imageSource: ImageSource?) {
        _uiState.update { it.copy(profilePicture = imageSource, profileCropTransform = null) }
    }

    fun onProfileTransformChanged(state: ImageTransformState) {
        _uiState.update { it.copy(profileCropTransform = state) }
    }

    fun updateFullName(fullName: String) {
        _uiState.update { it.copy(fullName = fullName) }
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun updateBirthDate(birthDate: LocalDate) {
        _uiState.update { it.copy(birthDate = birthDate) }
    }

    fun updateCountry(country: String) {
        _uiState.update { it.copy(country = country) }
    }

    fun updateCarImage(imageSource: ImageSource?) {
        _uiState.update { it.copy(carPicture = imageSource) }
    }

    fun updateCarBrand(brand: String) {
        _uiState.update {
            it.copy(
                selectedBrand = brand,
                selectedModel = "",
                selectedCarModelId = null,
                modelsForSelectedBrand = emptyList()
            )
        }

        viewModelScope.launch {
            loadModelsForBrand()
        }
    }

    fun updateCarModel(model: String) {
        _uiState.update { state ->
            val selectedModel = state.modelsForSelectedBrand.firstOrNull { it.model == model }
            state.copy(
                selectedModel = model,
                selectedCarModelId = selectedModel?.id
            )
        }
    }

    suspend fun loadCarBrands() {
        _uiState.update { it.copy(isFetchingBrands = true) }
        val brands = carModelRepository.getAllCarBrands()

        when (brands) {
            is ApiResult.Success -> {
                _uiState.update { it.copy(allBrands = brands.data) }
                Log.d("CAR BRANDS", "Car brands successfully loaded")
                Log.d("CAR BRANDS", _uiState.value.allBrands.toString())
            }

            is ApiResult.Error -> {
                setError(brands.message)
                Log.d("CAR BRANDS", "Error when loading car brands")
            }
        }
        _uiState.update { it.copy(isFetchingBrands = false) }

    }

    suspend fun loadModelsForBrand() {
        _uiState.update { it.copy(isFetchingModels = true) }
        val brand = _uiState.value.selectedBrand
        if (brand.isBlank()) {
            _uiState.update { it.copy(isFetchingModels = false, modelsForSelectedBrand = emptyList()) }
            return
        }

        val models = carModelRepository.getModelsForBrand(brand)

        when (models) {
            is ApiResult.Success -> {
                _uiState.update { it.copy(modelsForSelectedBrand = models.data) }
                Log.d(
                    "CAR MODELS",
                    "Loaded ${models.data.size} models for brand '$brand': ${models.data.map { it.model }}"
                )
            }

            is ApiResult.Error -> {
                setError(models.message)
                Log.d("CAR MODELS", "Error loading models for brand '$brand': ${models.message}")
            }
        }
        _uiState.update { it.copy(isFetchingModels = false) }

    }

    fun nextStep() {
        when (_uiState.value.currentStep) {
            ProfileStep.Personal -> {
                if (isPersonalInfoValid()) {
                    viewModelScope.launch {
                        if (!isUsernameAvailable()) return@launch

                        completeProfileSetup()
                    }
                } else {
                    setError("Please fill in all required fields")
                }
            }

            ProfileStep.Car -> {

            }
        }
    }

    fun previousStep() {
        when (_uiState.value.currentStep) {
            ProfileStep.Personal -> {
            }

            ProfileStep.Car -> {
                _uiState.update {
                    it.copy(
                        currentStep = ProfileStep.Personal,
                        errorMessage = null
                    )
                }
                logStepView(ProfileStep.Personal)
            }
        }
    }



    fun completeProfileSetup() {
        viewModelScope.launch {
            // Non-null once a userId is obtained (fresh POST /users or reused from a prior
            // attempt) — from that point on, a later-stage failure means an abandoned account
            // rather than a plain create attempt that never landed. Declared outside the try
            // block so the catch block below can read it too.
            var committedUserId: UUID? = null
            try {
                _uiState.update { it.copy(isLoading = true) }

                if (!isCarInfoValid()) {
                    logStageResult(STAGE_CAR_INFO_VALIDATION, success = false)
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                logStageResult(STAGE_CAR_INFO_VALIDATION, success = true)

                // A prior attempt may have already created the profile (e.g. this retry follows
                // an upload/car-creation failure right after a successful POST /users) — reuse
                // the persisted userId instead of calling createUserProfile() again, which would
                // hit a 409 UserProfileAlreadyExistsException and leave the user stuck with no
                // way to finish.
                val userId = userPreferences.userId.first() ?: createUserProfile() ?: run {
                    logStageResult(STAGE_USER_PROFILE, success = false)
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                logStageResult(STAGE_USER_PROFILE, success = true)
                committedUserId = userId

                if (!uploadProfileImageIfNeeded()) {
                    logStageResult(STAGE_PROFILE_IMAGE_UPLOAD, success = false)
                    logAbandonedAfterCommit(committedUserId)
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                logStageResult(STAGE_PROFILE_IMAGE_UPLOAD, success = true)

                if (!createUserCarIfNeeded(userId)) {
                    logStageResult(STAGE_USER_CAR, success = false)
                    logAbandonedAfterCommit(committedUserId)
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                logStageResult(STAGE_USER_CAR, success = true)

                // Arm the guided tour here: this is the genuine first entry into the main
                // app for a brand-new signup, right before the isUserCreated flag triggers
                // navigation to Feed. The combined Early Spotter card (if eligible) only shows
                // once the tour finishes — see EarlySpotterHostViewModel — so the two overlays
                // never race for the screen at once.
                userPreferences.setTourStatus(userId, TourStatus.Armed)
                logStageResult(STAGE_TOUR_ARM, success = true)
                _uiState.update { it.copy(isLoading = false, isUserCreated = true) }
                logStageResult(STAGE_COMPLETED, success = true)
                logOnboardingCompleted()
            } catch (e: Exception) {
                logStageResult(STAGE_UNEXPECTED_ERROR, success = false)
                logAbandonedAfterCommit(committedUserId)
                setError(e.message.toString())
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun isUsernameAvailable(): Boolean {
        _uiState.update { it.copy(isFetchingBrands = true, errorMessage = null) }

        return when (val result = userRepository.getUsersByUsername(_uiState.value.username.trim())) {
            is ApiResult.Success -> {
                val isAvailable = result.data.isEmpty()
                if (!isAvailable) {
                    setError("Username is already taken")
                }
                _uiState.update { it.copy(isFetchingBrands = false) }
                isAvailable
            }

            is ApiResult.Error -> {
                setError(result.message)
                _uiState.update { it.copy(isFetchingBrands = false) }
                false
            }
        }
    }

    private suspend fun createUserProfile(): UUID? {
        val createUserRequest = CreateUserRequest(
            fullName = _uiState.value.fullName.trim(),
            birthDate = _uiState.value.birthDate!!,
            username = _uiState.value.username.trim(),
            country = _uiState.value.country.trim(),
        )
        return when (val result = userRepository.createUser(createUserRequest)) {
            is ApiResult.Success -> {
                tokenStore.save(AuthTokens(result.data.accessToken, result.data.refreshToken))
                userPreferences.saveUserId(result.data.userId)
                earlySpotterController.onProfileCreated(
                    isEarlySpotter = result.data.isEarlySpotter,
                    earlySpotterNumber = result.data.earlySpotterNumber,
                    bonusPoints = result.data.earlySpotterBonusPoints,
                )
                result.data.userId
            }
            is ApiResult.Error -> {
                // The server's 409 UserProfileAlreadyExistsException has no machine-readable code
                // (see UserRoutes.kt), so this matches its message text — same defensive pattern
                // AuthRoutes.kt uses for its own provider-mismatch 409. A concurrent call (e.g. a
                // double-tap race) may have already saved the userId locally by the time this
                // branch runs; recover instead of blocking with a terminal error.
                if (result.message.contains("already exists", ignoreCase = true)) {
                    val existingUserId = userPreferences.userId.first()
                    if (existingUserId != null) {
                        return existingUserId
                    }
                    setError("Your profile may already have been created. Please try again.")
                    Log.d("ERROR", "Profile already exists but no local userId to recover")
                    return null
                }
                setError(result.message)
                Log.d("ERROR", "Error in user profile creation")
                null
            }
        }
    }

    private suspend fun createUserCarIfNeeded(userId: UUID): Boolean {
        val brand = _uiState.value.selectedBrand
        val model = _uiState.value.selectedModel
        val carModelId = _uiState.value.selectedCarModelId
        val carPicture = _uiState.value.carPicture

        if(brand.isBlank() && model.isBlank()) {
            return true
        }

        if(carModelId == null) {
            setError("Please select a valid car brand and model")
            return false
        }

        if (carPicture !is ImageSource.Local) {
            setError("The server currently requires a car image to save your car")
            return false
        }

        val compressedImage = imageCompressor.compressCarImage(carPicture.uri)
        val userCarRequest = UserCarRequest(
            userId = userId,
            carModelId = carModelId,
            imagePath = null,
        )
        return when (val result = userCarRepository.createMyCar(
            request = userCarRequest,
            imageBytes = compressedImage.bytes,
            mimeType = compressedImage.mimeType
        )) {
            is ApiResult.Success -> true
            is ApiResult.Error -> {
                setError(result.message)
                false
            }
        }
    }

    private suspend fun uploadProfileImageIfNeeded(): Boolean {
        val profilePicture = _uiState.value.profilePicture as? ImageSource.Local ?: return true

        return try {
            val cropTransform = _uiState.value.profileCropTransform?.toCropTransformOrNull()
            val compressedImage = if (cropTransform != null) {
                imageCompressor.compressWithCrop(
                    profilePicture.uri,
                    ImageCompressor.ProfileParams,
                    cropTransform
                )
            } else {
                imageCompressor.compressProfileImage(profilePicture.uri)
            }
            when (val result = userRepository.uploadProfilePicture(
                imageBytes = compressedImage.bytes,
                mimeType = compressedImage.mimeType
            )) {
                is ApiResult.Success -> true
                is ApiResult.Error -> {
                    setError(result.message)
                    false
                }
            }
        } catch (e: Exception) {
            setError(e.message ?: "Failed to upload profile picture")
            false
        }
    }

    fun isPersonalInfoValid(): Boolean {
        val state = _uiState.value
        return state.fullName.isNotBlank() &&
                state.username.isNotBlank() &&
                state.birthDate != null &&
                state.country.isNotBlank()
    }

    private fun isCarInfoValid(): Boolean {
        val state = _uiState.value
        val hasCarPicture = state.carPicture != null
        val hasBrand = state.selectedBrand.isNotBlank()
        val hasModel = state.selectedModel.isNotBlank()

        if (hasCarPicture && (!hasBrand || !hasModel)) {
            setError("Please select your car brand and model")
            return false
        }

        if (hasBrand != hasModel) {
            setError("Please select both car brand and model")
            return false
        }

        if (hasBrand && !hasCarPicture) {
            setError("The server currently requires a car image to save your car")
            return false
        }

        if (hasBrand && state.selectedCarModelId == null) {
            setError("Please select a valid car model")
            return false
        }

        return true
    }

    private fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message, isLoading = false) }

        viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

}

private fun ImageTransformState.toCropTransformOrNull(): CropTransform? {
    if (imageSize.width <= 0f || imageSize.height <= 0f) return null
    if (containerSize.width <= 0 || containerSize.height <= 0) return null
    return CropTransform(
        scale = scale,
        offsetX = offset.x,
        offsetY = offset.y,
        containerW = containerSize.width.toFloat(),
        containerH = containerSize.height.toFloat(),
    )
}
