package com.example.carspotter.features.settings.personalinfo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carspotter.core.image.CropTransform
import com.example.carspotter.core.image.ImageCompressor
import com.example.carspotter.core.network.ApiResult
import com.example.carspotter.data.remote.dto.user.UpdateUserRequest
import com.example.carspotter.data.repository.UserRepository
import com.example.carspotter.features.profile.components.ImageTransformState
import com.example.carspotter.features.profile.customization.ImageSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PersonalInfoViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val imageCompressor: ImageCompressor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalInfoUiState())
    val uiState: StateFlow<PersonalInfoUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = userRepository.getCurrentUser()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        user = result.data,
                        fullName = result.data.fullName,
                        username = result.data.username,
                        country = result.data.country,
                        birthDate = result.data.birthDate,
                        phoneNumber = result.data.phoneNumber.orEmpty(),
                        isLoading = false,
                    )
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, generalError = result.message)
                }
            }
        }
    }

    fun onFullNameChanged(value: String) {
        _uiState.update { it.copy(fullName = value, fieldErrors = it.fieldErrors - PersonalInfoField.FULL_NAME) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, fieldErrors = it.fieldErrors - PersonalInfoField.USERNAME) }
    }

    fun onCountryChanged(value: String) {
        _uiState.update { it.copy(country = value, fieldErrors = it.fieldErrors - PersonalInfoField.COUNTRY) }
    }

    fun onBirthDateChanged(value: LocalDate) {
        _uiState.update { it.copy(birthDate = value, fieldErrors = it.fieldErrors - PersonalInfoField.BIRTH_DATE) }
    }

    fun onPhoneNumberChanged(value: String) {
        _uiState.update { it.copy(phoneNumber = value, fieldErrors = it.fieldErrors - PersonalInfoField.PHONE_NUMBER) }
    }

    fun onImagePicked(uri: Uri) {
        _uiState.update {
            it.copy(
                pendingImage = ImageSource.Local(uri),
                cropTransform = null,
                isPositioningImage = true,
            )
        }
    }

    fun onTransformChanged(state: ImageTransformState) {
        _uiState.update { it.copy(cropTransform = state) }
    }

    fun onDoneImage() {
        val pendingImage = _uiState.value.pendingImage ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isPositioningImage = false, isUploadingImage = true, generalError = null) }

            try {
                val cropTransform = _uiState.value.cropTransform?.toCropTransformOrNull()
                val compressedImage = if (cropTransform != null) {
                    imageCompressor.compressWithCrop(pendingImage.uri, ImageCompressor.ProfileParams, cropTransform)
                } else {
                    imageCompressor.compressProfileImage(pendingImage.uri)
                }

                when (
                    val result = userRepository.uploadProfilePicture(
                        imageBytes = compressedImage.bytes,
                        mimeType = compressedImage.mimeType,
                    )
                ) {
                    is ApiResult.Success -> _uiState.update {
                        it.copy(
                            user = result.data,
                            pendingImage = null,
                            cropTransform = null,
                            isUploadingImage = false,
                        )
                    }

                    is ApiResult.Error -> _uiState.update {
                        it.copy(isUploadingImage = false, generalError = result.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUploadingImage = false, generalError = e.message ?: "Failed to upload profile picture")
                }
            }
        }
    }

    fun onSave() {
        val state = _uiState.value
        val user = state.user ?: return
        if (state.isSaving) return

        val request = UpdateUserRequest(
            fullName = state.fullName.trim().takeIf { it.isNotBlank() && it != user.fullName },
            username = state.username.trim().takeIf { it.isNotBlank() && it != user.username },
            country = state.country.trim().takeIf { it.isNotBlank() && it != user.country },
            birthDate = state.birthDate?.takeIf { it != user.birthDate },
            phoneNumber = state.phoneNumber.trim().takeIf { it.isNotBlank() && it != user.phoneNumber.orEmpty() },
        )

        val hasChanges = listOf(
            request.fullName,
            request.username,
            request.country,
            request.birthDate,
            request.phoneNumber,
        ).any { it != null }

        if (!hasChanges) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isSaving = true, generalError = null, fieldErrors = emptyMap(), saveSuccess = false)
            }

            when (val result = userRepository.updateUser(request)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(user = result.data, isSaving = false, saveSuccess = true)
                }

                is ApiResult.Error -> _uiState.update {
                    val fieldErrors = mapErrorToFields(result.message)
                    it.copy(
                        isSaving = false,
                        fieldErrors = fieldErrors,
                        generalError = result.message.takeIf { fieldErrors.isEmpty() },
                    )
                }
            }
        }
    }

    private fun mapErrorToFields(message: String): Map<PersonalInfoField, String> {
        val lower = message.lowercase()
        val field = when {
            "username" in lower -> PersonalInfoField.USERNAME
            "phone" in lower -> PersonalInfoField.PHONE_NUMBER
            "full name" in lower -> PersonalInfoField.FULL_NAME
            "country" in lower -> PersonalInfoField.COUNTRY
            "birth date" in lower -> PersonalInfoField.BIRTH_DATE
            else -> null
        }

        return if (field != null) mapOf(field to message) else emptyMap()
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
