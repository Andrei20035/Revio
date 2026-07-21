package com.revio.app.features.upload

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.app.core.image.CropTransform
import com.revio.app.core.image.ImageCompressor
import com.revio.app.core.network.ApiResult
import com.revio.app.features.profile.components.ImageTransformState
import com.revio.app.core.navigation.Screen
import com.revio.app.data.remote.dto.post.CreatePostMetadata
import com.revio.app.data.remote.dto.post.UpdatePostRequest
import com.revio.app.data.repository.CarModelRepository
import com.revio.app.data.repository.LocationRepository
import com.revio.app.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ImageUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val carModelRepository: CarModelRepository,
    private val postRepository: PostRepository,
    private val imageCompressor: ImageCompressor,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    // Guards against re-resolving location on recomposition / repeated permission callbacks.
    private var locationResolutionStarted = false

    private val _uiState = MutableStateFlow(ImageUploadUiState())
    val uiState: StateFlow<ImageUploadUiState> = _uiState.asStateFlow()

    init {
        val postId = savedStateHandle
            .get<String>(Screen.ImageUpload.ARG_POST_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        if (postId != null) {
            _uiState.update { it.copy(postId = postId) }
            loadExistingPost(postId)
        } else {
            val imageUri = savedStateHandle
                .get<String>(Screen.ImageUpload.ARG_IMAGE_URI)
                ?.takeIf { it.isNotBlank() }
                ?.let(Uri::parse)
            val source = savedStateHandle
                .get<String>(Screen.ImageUpload.ARG_SOURCE)
                ?.takeIf { it == "CAMERA" || it == "GALLERY" }
                ?: "GALLERY"
            _uiState.update { it.copy(imageUri = imageUri, postSource = source) }
        }
        loadBrands()
    }

    // ---- Edit mode: prefill from the existing post ----

    private fun loadExistingPost(postId: UUID) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPost = true) }
            when (val result = postRepository.getPostDetail(postId)) {
                is ApiResult.Success -> {
                    val post = result.data
                    _uiState.update {
                        it.copy(
                            isLoadingPost = false,
                            existingImageUrl = post.imageUrl,
                            description = post.caption.orEmpty(),
                            selectedBrand = post.brand,
                        )
                    }
                    prefillModel(post.brand, post.model)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoadingPost = false, userMessage = result.message)
                }
            }
        }
    }

    /**
     * Like [loadModels], but also selects the [CarModelOption] matching [modelName] once the
     * list loads, so the edit screen can prefill [ImageUploadUiState.selectedModel] (needed to
     * send a `carModelId` back on save). If no catalog model matches (e.g. a custom car), the
     * selection is left null and the user must pick one explicitly.
     */
    private suspend fun prefillModel(brand: String, modelName: String) {
        _uiState.update { it.copy(isLoadingModels = true, modelsError = null) }
        when (val result = carModelRepository.getModelsForBrand(brand)) {
            is ApiResult.Success -> _uiState.update {
                if (it.selectedBrand != brand) it
                else it.copy(
                    models = result.data,
                    isLoadingModels = false,
                    selectedModel = result.data.firstOrNull { option -> option.model == modelName },
                )
            }
            is ApiResult.Error -> _uiState.update {
                if (it.selectedBrand != brand) it
                else it.copy(isLoadingModels = false, modelsError = result.message)
            }
        }
    }

    // ---- Brands ----

    fun loadBrands() {
        if (_uiState.value.isLoadingBrands) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBrands = true, brandsError = null) }
            when (val result = carModelRepository.getAllCarBrands()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(brands = result.data, isLoadingBrands = false)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoadingBrands = false, brandsError = result.message)
                }
            }
        }
    }

    fun onBrandFieldClick() {
        val state = _uiState.value
        when {
            state.isLoadingBrands -> Unit
            state.brands.isEmpty() -> loadBrands() // recover from an earlier error / empty
            else -> _uiState.update { it.copy(brandDropdownOpen = true) }
        }
    }

    fun dismissBrandDropdown() = _uiState.update { it.copy(brandDropdownOpen = false) }

    fun onBrandSelected(brand: String) {
        if (brand == _uiState.value.selectedBrand) {
            _uiState.update { it.copy(brandDropdownOpen = false) }
            return
        }
        // New brand → reset model selection and reload models for it.
        _uiState.update {
            it.copy(
                selectedBrand = brand,
                brandDropdownOpen = false,
                selectedModel = null,
                models = emptyList(),
                modelsError = null,
            )
        }
        loadModels(brand)
    }

    // ---- Models ----

    fun loadModels(brand: String) {
        if (_uiState.value.isLoadingModels) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true, modelsError = null) }
            when (val result = carModelRepository.getModelsForBrand(brand)) {
                is ApiResult.Success -> _uiState.update {
                    // Ignore if the user switched brands meanwhile.
                    if (it.selectedBrand != brand) it
                    else it.copy(models = result.data, isLoadingModels = false)
                }
                is ApiResult.Error -> _uiState.update {
                    if (it.selectedBrand != brand) it
                    else it.copy(isLoadingModels = false, modelsError = result.message)
                }
            }
        }
    }

    fun onModelFieldClick() {
        val state = _uiState.value
        when {
            state.selectedBrand == null || state.isLoadingModels -> Unit
            state.models.isEmpty() -> state.selectedBrand.let(::loadModels) // retry
            else -> _uiState.update { it.copy(modelDropdownOpen = true) }
        }
    }

    fun dismissModelDropdown() = _uiState.update { it.copy(modelDropdownOpen = false) }

    fun onModelSelected(modelName: String) {
        val option = _uiState.value.models.firstOrNull { it.model == modelName }
        _uiState.update { it.copy(selectedModel = option ?: it.selectedModel, modelDropdownOpen = false) }
    }

    // ---- Location (optional, best-effort, never blocks posting) ----

    /** True if foreground location permission is already granted. */
    fun hasLocationPermission(): Boolean = locationRepository.hasLocationPermission()

    /** Called once with the outcome of the foreground-location permission request. */
    fun onLocationPermissionResult(granted: Boolean) {
        // Editing an existing post never touches its location.
        if (_uiState.value.isEditMode) return

        if (granted) {
            resolveLocation()
        } else {
            // Denied → keep location null and let the user post normally.
            _uiState.update { it.copy(locationStatus = LocationStatus.Unavailable) }
        }
    }

    private fun resolveLocation() {
        if (locationResolutionStarted || _uiState.value.isEditMode) return
        locationResolutionStarted = true

        viewModelScope.launch {
            _uiState.update { it.copy(locationStatus = LocationStatus.Resolving) }

            val coordinates = locationRepository.getCurrentLocation()
            if (coordinates == null) {
                // No fix / disabled / timeout → silently post without location.
                _uiState.update { it.copy(locationStatus = LocationStatus.Unavailable) }
                return@launch
            }

            // Coordinates are enough; town/country are a best-effort bonus.
            val place = locationRepository.reverseGeocode(coordinates)
            _uiState.update {
                it.copy(
                    latitude = coordinates.latitude,
                    longitude = coordinates.longitude,
                    town = place?.town,
                    country = place?.country,
                    locationStatus = LocationStatus.Added,
                )
            }
        }
    }

    // ---- Description ----

    fun onDescriptionChange(text: String) = _uiState.update { it.copy(description = text) }

    // ---- Transform (pinch/pan from the preview card) ----

    fun onTransformChanged(state: ImageTransformState) =
        _uiState.update { it.copy(cropTransform = state) }

    // ---- Post ----

    fun post() {
        val state = _uiState.value
        if (!state.canPost) return

        if (state.isEditMode) {
            updateExistingPost(state)
        } else {
            createNewPost(state)
        }
    }

    private fun createNewPost(state: ImageUploadUiState) {
        val imageUri = state.imageUri
        val model = state.selectedModel
        if (imageUri == null || model == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true) }

            val cropTransform = state.cropTransform?.toCropTransformOrNull()
            val compressed = runCatching {
                if (cropTransform != null) {
                    imageCompressor.compressWithCrop(imageUri, ImageCompressor.CarParams, cropTransform)
                } else {
                    imageCompressor.compress(imageUri, ImageCompressor.CarParams)
                }
            }.getOrElse {
                _uiState.update {
                    it.copy(isPosting = false, userMessage = "Couldn't process the image. Please try again.")
                }
                return@launch
            }

            // Attach whatever location has resolved by now (may be partial or absent) — never blocks.
            val metadata = CreatePostMetadata(
                carModelId = model.id,
                caption = state.description.trim().ifBlank { null },
                latitude = state.latitude,
                longitude = state.longitude,
                town = state.town,
                country = state.country,
                source = state.postSource,
                createdAtTimezone = ZoneId.systemDefault().id,
            )

            when (val result = postRepository.createPost(metadata, compressed.bytes, compressed.mimeType)) {
                is ApiResult.Success -> _uiState.update { it.copy(isPosting = false, postSuccess = true) }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isPosting = false, userMessage = result.message)
                }
            }
        }
    }

    private fun updateExistingPost(state: ImageUploadUiState) {
        val postId = state.postId ?: return
        val model = state.selectedModel ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true) }

            val request = UpdatePostRequest(
                carModelId = model.id,
                caption = state.description.trim().ifBlank { null },
            )

            when (val result = postRepository.updatePost(postId, request)) {
                is ApiResult.Success -> _uiState.update { it.copy(isPosting = false, postSuccess = true) }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isPosting = false, userMessage = result.message)
                }
            }
        }
    }

    fun consumeUserMessage() = _uiState.update { it.copy(userMessage = null) }
}

/**
 * Converts [ImageTransformState] to [CropTransform] only when geometry is fully available:
 * - imageSize is specified (image has finished loading in the preview)
 * - containerSize is non-zero (layout has been measured)
 * Returns null when either piece is missing → caller falls back to center-crop.
 */
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
