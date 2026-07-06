package com.example.carspotter.features.settings.personalinfo

import com.example.carspotter.data.model.User
import com.example.carspotter.features.profile.customization.ImageSource
import com.example.carspotter.features.profile.components.ImageTransformState
import java.time.LocalDate

enum class PersonalInfoField {
    FULL_NAME,
    USERNAME,
    COUNTRY,
    BIRTH_DATE,
    PHONE_NUMBER,
}

data class PersonalInfoUiState(
    val user: User? = null,

    val fullName: String = "",
    val username: String = "",
    val country: String = "",
    val birthDate: LocalDate? = null,
    val phoneNumber: String = "",

    val pendingImage: ImageSource.Local? = null,
    val cropTransform: ImageTransformState? = null,
    val isPositioningImage: Boolean = false,
    val isUploadingImage: Boolean = false,

    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,

    val fieldErrors: Map<PersonalInfoField, String> = emptyMap(),
    val generalError: String? = null,
)
