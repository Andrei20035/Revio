package com.revio.social.features.profile.customization

import android.net.Uri
import com.revio.social.data.remote.dto.auth.WaitlistUsernameStatus
import com.revio.social.data.remote.dto.car_model.CarModelOption
import com.revio.social.features.profile.components.ImageTransformState
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
    // Waitlist prefill: suggestedUsername comes raw from AuthResponse.waitlist (threaded through
    // nav args, see Screen.ProfileCustomization) and may not satisfy Revio's username rules —
    // suggestedUsernameStatus is exactly what the server already computed for it. ViewModel.init
    // copies suggestedUsername into username explicitly on startup; username then stays fully
    // editable via updateUsername(). country is intentionally NOT prefilled from the waitlist.
    val suggestedUsername: String? = null,
    val suggestedUsernameStatus: WaitlistUsernameStatus? = null,
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
