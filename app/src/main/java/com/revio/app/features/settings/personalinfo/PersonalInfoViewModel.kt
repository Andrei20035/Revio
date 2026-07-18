package com.revio.app.features.settings.personalinfo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.app.core.image.CropTransform
import com.revio.app.core.image.ImageCompressor
import com.revio.app.core.network.ApiResult
import com.revio.app.data.model.User
import com.revio.app.data.remote.dto.user.UpdateUserRequest
import com.revio.app.data.repository.UserRepository
import com.revio.app.features.profile.components.ImageTransformState
import com.revio.app.features.profile.customization.ImageSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MIN_USERNAME_LENGTH = 3
private const val MAX_USERNAME_LENGTH = 50
private val USERNAME_REGEX = Regex("^[a-z0-9._]+$")
private const val USERNAME_CHECK_DEBOUNCE_MS = 400L

private val PROFILE_RESTRICTION_ERROR_CODE_TO_FIELD = mapOf(
    "FULL_NAME_ALREADY_CHANGED" to PersonalInfoField.FULL_NAME,
    "COUNTRY_ALREADY_CHANGED" to PersonalInfoField.COUNTRY,
    "BIRTH_DATE_ALREADY_CHANGED" to PersonalInfoField.BIRTH_DATE,
    "USERNAME_CHANGE_TOO_SOON" to PersonalInfoField.USERNAME,
    "PHONE_NUMBER_CHANGE_TOO_SOON" to PersonalInfoField.PHONE_NUMBER,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PersonalInfoViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val imageCompressor: ImageCompressor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalInfoUiState())
    val uiState: StateFlow<PersonalInfoUiState> = _uiState.asStateFlow()

    private val usernameQueryFlow = MutableStateFlow<String?>(null)
    private var loadCurrentUserJob: Job? = null

    init {
        loadCurrentUser()
        observeUsernameAvailability()
    }

    private fun loadCurrentUser() {
        if (loadCurrentUserJob?.isActive == true) return

        loadCurrentUserJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalError = null) }
//            delay(2500) // TEMP: simulates a slow server for manual lag testing — remove after testing.
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
                        canChange = buildCanChangeMap(result.data),
                        unlockAt = buildUnlockAtMap(result.data),
                        usernameCheck = UsernameCheckState.Idle,
                        pendingPermanentFields = emptySet(),
                    )
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, generalError = result.message)
                }
            }
        }
    }

    fun retryLoadCurrentUser() {
        loadCurrentUser()
    }

    private fun refreshEligibility() {
        viewModelScope.launch {
            when (val result = userRepository.getCurrentUser()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        user = result.data,
                        canChange = buildCanChangeMap(result.data),
                        unlockAt = buildUnlockAtMap(result.data),
                    )
                }

                is ApiResult.Error -> Unit
            }
        }
    }

    private fun buildCanChangeMap(user: User): Map<PersonalInfoField, Boolean> = mapOf(
        PersonalInfoField.FULL_NAME to user.canChangeFullName,
        PersonalInfoField.COUNTRY to user.canChangeCountry,
        PersonalInfoField.BIRTH_DATE to user.canChangeBirthDate,
        PersonalInfoField.USERNAME to user.canChangeUsername,
        PersonalInfoField.PHONE_NUMBER to user.canChangePhoneNumber,
    )

    private fun buildUnlockAtMap(user: User): Map<PersonalInfoField, java.time.Instant> = buildMap {
        user.nextUsernameChangeAt?.let { put(PersonalInfoField.USERNAME, it) }
        user.nextPhoneNumberChangeAt?.let { put(PersonalInfoField.PHONE_NUMBER, it) }
    }

    private fun recomputePendingPermanentFields(state: PersonalInfoUiState): Set<PersonalInfoField> {
        val user = state.user ?: return emptySet()
        return buildSet {
            if (state.fullName.trim().isNotBlank() && state.fullName.trim() != user.fullName) {
                add(PersonalInfoField.FULL_NAME)
            }
            if (state.country.trim().isNotBlank() && state.country.trim() != user.country) {
                add(PersonalInfoField.COUNTRY)
            }
            if (state.birthDate != null && state.birthDate != user.birthDate) {
                add(PersonalInfoField.BIRTH_DATE)
            }
        }
    }

    fun onFullNameChanged(value: String) {
        _uiState.update {
            val updated = it.copy(fullName = value, fieldErrors = it.fieldErrors - PersonalInfoField.FULL_NAME)
            updated.copy(pendingPermanentFields = recomputePendingPermanentFields(updated))
        }
    }

    fun onUsernameChanged(value: String) {
        val user = _uiState.value.user
        val normalized = value.trim().lowercase()

        val newCheckState = when {
            normalized.isBlank() -> UsernameCheckState.Invalid("Username cannot be blank")
            user != null && normalized == user.username.lowercase() -> UsernameCheckState.Unchanged
            normalized.length < MIN_USERNAME_LENGTH -> UsernameCheckState.Invalid(
                "Username must be between $MIN_USERNAME_LENGTH and $MAX_USERNAME_LENGTH characters"
            )
            normalized.length > MAX_USERNAME_LENGTH -> UsernameCheckState.Invalid(
                "Username must be between $MIN_USERNAME_LENGTH and $MAX_USERNAME_LENGTH characters"
            )
            !USERNAME_REGEX.matches(normalized) -> UsernameCheckState.Invalid(
                "Username may contain only lowercase letters, digits, dot, and underscore"
            )
            else -> UsernameCheckState.Checking
        }

        _uiState.update {
            it.copy(username = value, fieldErrors = it.fieldErrors - PersonalInfoField.USERNAME, usernameCheck = newCheckState)
        }

        if (newCheckState is UsernameCheckState.Checking) {
            usernameQueryFlow.value = normalized
        } else {
            usernameQueryFlow.value = null
        }
    }

    private fun observeUsernameAvailability() {
        viewModelScope.launch {
            usernameQueryFlow
                .filterNotNull()
                .debounce(USERNAME_CHECK_DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { normalized ->
                    flow { emit(normalized to checkAvailability(normalized)) }
                }
                .collect { (normalized, checkState) ->
                    _uiState.update { state ->
                        val currentNormalized = state.username.trim().lowercase()
                        if (currentNormalized != normalized) state else state.copy(usernameCheck = checkState)
                    }
                }
        }
    }

    private suspend fun checkAvailability(normalized: String): UsernameCheckState {
        return when (val result = userRepository.checkUsernameAvailability(normalized)) {
            is ApiResult.Success -> {
                if (result.data.normalized != normalized) {
                    UsernameCheckState.NetworkError
                } else if (result.data.available) {
                    UsernameCheckState.Available
                } else {
                    UsernameCheckState.Taken
                }
            }

            is ApiResult.Error -> UsernameCheckState.NetworkError
        }
    }

    fun onCountryChanged(value: String) {
        _uiState.update {
            val updated = it.copy(country = value, fieldErrors = it.fieldErrors - PersonalInfoField.COUNTRY)
            updated.copy(pendingPermanentFields = recomputePendingPermanentFields(updated))
        }
    }

    fun onBirthDateChanged(value: LocalDate) {
        _uiState.update {
            val updated = it.copy(birthDate = value, fieldErrors = it.fieldErrors - PersonalInfoField.BIRTH_DATE)
            updated.copy(pendingPermanentFields = recomputePendingPermanentFields(updated))
        }
    }

    fun onPhoneNumberChanged(value: String) {
        _uiState.update { it.copy(phoneNumber = value, fieldErrors = it.fieldErrors - PersonalInfoField.PHONE_NUMBER) }
    }

    fun onFieldFocusChanged(field: PersonalInfoField?) {
        _uiState.update { it.copy(focusedField = field) }
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
        if (state.isSaving || state.isSaveBlocked) return

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
                    it.copy(
                        user = result.data,
                        isSaving = false,
                        saveSuccess = true,
                        canChange = buildCanChangeMap(result.data),
                        unlockAt = buildUnlockAtMap(result.data),
                        pendingPermanentFields = emptySet(),
                    )
                }

                is ApiResult.Error -> {
                    val fieldErrors = mapErrorToFields(result.message, result.code)
                    val restrictedField = PROFILE_RESTRICTION_ERROR_CODE_TO_FIELD[result.code]
                    val isUsernameConflict = fieldErrors.containsKey(PersonalInfoField.USERNAME) && restrictedField == null

                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            fieldErrors = fieldErrors,
                            generalError = result.message.takeIf { fieldErrors.isEmpty() },
                            usernameCheck = if (isUsernameConflict) UsernameCheckState.Taken else it.usernameCheck,
                        )
                    }

                    if (restrictedField != null) {
                        refreshEligibility()
                    }
                }
            }
        }
    }

    private fun mapErrorToFields(message: String, code: String?): Map<PersonalInfoField, String> {
        val field = PROFILE_RESTRICTION_ERROR_CODE_TO_FIELD[code] ?: run {
            val lower = message.lowercase()
            when {
                "username" in lower -> PersonalInfoField.USERNAME
                "phone" in lower -> PersonalInfoField.PHONE_NUMBER
                "full name" in lower -> PersonalInfoField.FULL_NAME
                "country" in lower -> PersonalInfoField.COUNTRY
                "birth date" in lower -> PersonalInfoField.BIRTH_DATE
                else -> null
            }
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
