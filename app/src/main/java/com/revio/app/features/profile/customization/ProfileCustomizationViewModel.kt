package com.revio.app.features.profile.customization

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.app.core.image.CropTransform
import com.revio.app.core.image.ImageCompressor
import com.revio.app.features.profile.components.ImageTransformState
import com.revio.app.data.local.preferences.UserPreferences
import com.revio.app.data.local.auth.AuthTokens
import com.revio.app.data.local.auth.TokenStore
import com.revio.app.data.remote.dto.user.CreateUserRequest
import com.revio.app.data.remote.dto.user_car.UserCarRequest
import com.revio.app.core.network.ApiResult
import com.revio.app.data.repository.CarModelRepository
import com.revio.app.data.repository.UserCarRepository
import com.revio.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProfileCustomizationViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userCarRepository: UserCarRepository,
    private val userPreferences: UserPreferences,
    private val carModelRepository: CarModelRepository,
    private val imageCompressor: ImageCompressor,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileCustomizationUiState())
    val uiState: StateFlow<ProfileCustomizationUiState> = _uiState.asStateFlow()

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
            }
        }
    }



    fun completeProfileSetup() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                if (!isCarInfoValid()) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val userId = createUserProfile() ?: run {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                if (!uploadProfileImageIfNeeded()) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                if (!createUserCarIfNeeded(userId)) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                _uiState.update { it.copy(isLoading = false, isUserCreated = true) }
            } catch (e: Exception) {
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
        Log.d("BIRTHDATE" ,_uiState.value.birthDate.toString())
        return when (val result = userRepository.createUser(createUserRequest)) {
            is ApiResult.Success -> {
                tokenStore.save(AuthTokens(result.data.accessToken, result.data.refreshToken))
                userPreferences.saveUserId(result.data.userId)
                result.data.userId
            }
            is ApiResult.Error -> {
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
