package com.revio.app.features.profile.customization

import android.net.Uri
import com.revio.app.data.remote.dto.car_model.CarModelOption
import com.revio.app.features.profile.components.ImageTransformState
import java.time.LocalDate
import java.util.UUID

sealed class ImageSource {
    data class Local(val uri: Uri, val mimeType: String? = null) : ImageSource()
    data class Remote(val url: String, val mimeType: String? = null) : ImageSource()
}

sealed class ProfileStep {
    object Personal: ProfileStep()
    object Car: ProfileStep()
}

data class ProfileCustomizationUiState(
    val allBrands: List<String> = emptyList(),
    val modelsForSelectedBrand: List<CarModelOption> = emptyList(),

    val profilePicture: ImageSource? = null,
    val profileCropTransform: ImageTransformState? = null,
    val fullName: String = "",
    val username: String = "",
    val country: String = "",
    val birthDate: LocalDate? = null,

    val carPicture: ImageSource? = null,
    val selectedBrand: String = "",
    val selectedModel: String = "",
    val selectedCarModelId: UUID? = null,
    val isFetchingBrands: Boolean = false,
    val isFetchingModels: Boolean = false,

    val currentStep: ProfileStep = ProfileStep.Personal, // TODO: Modify in production
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isUserCreated: Boolean = false
)
