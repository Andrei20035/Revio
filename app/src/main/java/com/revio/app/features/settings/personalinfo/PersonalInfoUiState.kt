package com.revio.app.features.settings.personalinfo

import com.revio.app.data.model.User
import com.revio.app.features.profile.customization.ImageSource
import com.revio.app.features.profile.components.ImageTransformState
import java.time.Instant
import java.time.LocalDate

enum class PersonalInfoField {
    FULL_NAME,
    USERNAME,
    COUNTRY,
    BIRTH_DATE,
    PHONE_NUMBER,
}

sealed class UsernameCheckState {
    data object Idle : UsernameCheckState()
    data object Unchanged : UsernameCheckState()
    data class Invalid(val reason: String) : UsernameCheckState()
    data object Checking : UsernameCheckState()
    data object Available : UsernameCheckState()
    data object Taken : UsernameCheckState()
    data object NetworkError : UsernameCheckState()
}

data class PersonalInfoUiState(
    val user: User? = null,

    val fullName: String = "",
    val username: String = "",
    val country: String = "",
    val birthDate: LocalDate? = null,
    val phoneNumber: String = "",

    val canChange: Map<PersonalInfoField, Boolean> = emptyMap(),
    val unlockAt: Map<PersonalInfoField, Instant> = emptyMap(),
    val usernameCheck: UsernameCheckState = UsernameCheckState.Idle,
    val pendingPermanentFields: Set<PersonalInfoField> = emptySet(),
    val focusedField: PersonalInfoField? = null,

    val pendingImage: ImageSource.Local? = null,
    val cropTransform: ImageTransformState? = null,
    val isPositioningImage: Boolean = false,
    val isUploadingImage: Boolean = false,

    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,

    val fieldErrors: Map<PersonalInfoField, String> = emptyMap(),
    val generalError: String? = null,
) {
    val isSaveBlocked: Boolean
        get() = isSaving ||
            usernameCheck is UsernameCheckState.Checking ||
            usernameCheck is UsernameCheckState.Invalid ||
            usernameCheck is UsernameCheckState.Taken
}
