package com.revio.social.features.upload

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.feedback.PostCreatedEvent
import com.revio.social.core.feedback.PostCreationSignal
import com.revio.social.core.image.CropTransform
import com.revio.social.core.image.ImageCompressor
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ERROR_CODE_NETWORK
import com.revio.social.core.network.isNetworkError
import com.revio.social.features.profile.components.ImageTransformState
import com.revio.social.core.navigation.Screen
import com.revio.social.data.remote.dto.post.CreatePostMetadata
import com.revio.social.data.remote.dto.post.UpdatePostRequest
import com.revio.social.data.repository.CarModelRepository
import com.revio.social.data.repository.LocationRepository
import com.revio.social.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** ev. 15 — fired once per attempt, right before compression starts. */
private const val EVENT_POST_CREATE_START = "post_create_start"

/**
 * ev. 16 — image compression outcome. A failure also records a Crashlytics non-fatal (taxonomy
 * category 1 — unexpected, see plan's error taxonomy for `ImageUploadViewModel.kt:306-317`).
 */
private const val EVENT_POST_COMPRESS_RESULT = "post_compress_result"

/**
 * ev. 17 — best-effort location resolution outcome (taxonomy category 4 — control flow, never a
 * non-fatal). `failure_code` fixed set: `permission_denied`, `permission_denied_permanently`,
 * `services_disabled`, `no_fix` (mirrors [LocationFailure]).
 */
private const val EVENT_POST_LOCATION_RESULT = "post_location_result"

/** Reported to Crashlytics on [EVENT_POST_COMPRESS_RESULT] failure — see pas 2.5a. */
private class ImageCompressionFailedException(cause: Throwable) : Exception("post_compress_failed", cause)

/**
 * ev. 18 — `POST /posts` result. `duration_bucket`/`retry_bucket` use the fixed vocabularies
 * frozen in docs/telemetry-naming-and-forbidden-data.md; `failure_code` is [ApiResult.Error.code]
 * when present, [com.revio.social.core.network.ERROR_CODE_NETWORK] for offline, or a fixed
 * fallback (`unrecognized`) otherwise — same pattern as pas 2.2b's `resolveAuthFailureCode`.
 */
private const val EVENT_POST_UPLOAD_RESULT = "post_upload_result"

private fun durationBucket(durationMs: Long): String = when {
    durationMs < 1_000 -> "lt_1s"
    durationMs < 3_000 -> "1_3s"
    durationMs < 10_000 -> "3_10s"
    durationMs < 30_000 -> "10_30s"
    else -> "gte_30s"
}

private fun retryBucket(retryCount: Int): String = when {
    retryCount <= 0 -> "0"
    retryCount == 1 -> "1"
    retryCount == 2 -> "2"
    else -> "gte_3"
}

@HiltViewModel
class ImageUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val carModelRepository: CarModelRepository,
    private val postRepository: PostRepository,
    private val imageCompressor: ImageCompressor,
    private val locationRepository: LocationRepository,
    private val postCreationSignal: PostCreationSignal,
    private val analyticsClient: AnalyticsClient? = null,
) : ViewModel() {

    // In-flight location resolution, if any. Prevents concurrent requests but allows retries
    // once the previous attempt has finished (unlike the one-shot latch this replaced).
    private var locationJob: Job? = null

    // Tracks failed create-post attempts for this VM instance, surfaced on PostCreatedEvent
    // so the first-post feedback prompt can factor upload friction into its analytics.
    private var createPostRetryCount = 0
    private var lastCreatePostErrorCode: String? = null

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
                            vehicleLocked = post.vehicleLocked,
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
            state.vehicleLocked -> _uiState.update { it.copy(showVehicleLockedInfo = true) }
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
            state.vehicleLocked -> _uiState.update { it.copy(showVehicleLockedInfo = true) }
            state.selectedBrand == null || state.isLoadingModels -> Unit
            state.models.isEmpty() -> state.selectedBrand.let(::loadModels) // retry
            else -> _uiState.update { it.copy(modelDropdownOpen = true) }
        }
    }

    fun dismissModelDropdown() = _uiState.update { it.copy(modelDropdownOpen = false) }

    fun dismissVehicleLockedInfo() = _uiState.update { it.copy(showVehicleLockedInfo = false) }

    fun onModelSelected(modelName: String) {
        val option = _uiState.value.models.firstOrNull { it.model == modelName }
        _uiState.update { it.copy(selectedModel = option ?: it.selectedModel, modelDropdownOpen = false) }
    }

    // ---- Location (optional, best-effort, never blocks posting) ----

    /** True if foreground location permission is already granted. */
    fun hasLocationPermission(): Boolean = locationRepository.hasLocationPermission()

    /** Called once with the outcome of the foreground-location permission request. */
    fun onLocationPermissionResult(granted: Boolean, permanentlyDenied: Boolean = false) {
        // Editing an existing post never touches its location.
        if (_uiState.value.isEditMode) return

        if (granted) {
            resolveLocation()
        } else {
            // Denied → keep location null and let the user post normally.
            val reason = if (permanentlyDenied) {
                LocationFailure.PermissionDeniedPermanently
            } else {
                LocationFailure.PermissionDenied
            }
            _uiState.update { it.copy(locationStatus = LocationStatus.Unavailable(reason)) }
        }
    }

    /**
     * User-initiated retry from the location chip. No-op while a resolution is already in
     * flight or in edit mode; otherwise starts a fresh best-effort resolution.
     */
    fun onRetryLocation() {
        if (locationJob?.isActive == true || _uiState.value.isEditMode) return
        resolveLocation()
    }

    private fun resolveLocation() {
        if (locationJob?.isActive == true || _uiState.value.isEditMode) return

        locationJob = viewModelScope.launch {
            _uiState.update { it.copy(locationStatus = LocationStatus.Resolving) }

            val coordinates = locationRepository.getCurrentLocation()
            if (coordinates == null) {
                // No fix / disabled / timeout → silently post without location.
                val reason = if (!locationRepository.locationServicesEnabled()) {
                    LocationFailure.ServicesDisabled
                } else {
                    LocationFailure.NoFix
                }
                _uiState.update { it.copy(locationStatus = LocationStatus.Unavailable(reason)) }
                logLocationResult(success = false, reason = reason)
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
                    locationStatus = LocationStatus.Resolved,
                )
            }
            logLocationResult(success = true)
        }
    }

    private fun logLocationResult(success: Boolean, reason: LocationFailure? = null) {
        val failureCode = when (reason) {
            LocationFailure.PermissionDenied -> "permission_denied"
            LocationFailure.PermissionDeniedPermanently -> "permission_denied_permanently"
            LocationFailure.ServicesDisabled -> "services_disabled"
            LocationFailure.NoFix -> "no_fix"
            null -> null
        }
        val params = buildMap<String, AnalyticsParamValue> {
            put("outcome", AnalyticsParamValue.StringValue(if (success) "success" else "failure"))
            failureCode?.let { put("failure_code", AnalyticsParamValue.StringValue(it)) }
        }
        analyticsClient?.log(AnalyticsEvent(name = EVENT_POST_LOCATION_RESULT, params = params))
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

    private fun logCompressResult(success: Boolean) {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_POST_COMPRESS_RESULT,
                params = mapOf("outcome" to AnalyticsParamValue.StringValue(if (success) "success" else "failure")),
            )
        )
    }

    private fun logUploadResult(success: Boolean, durationMs: Long, retryCount: Int, failureCode: String? = null) {
        val params = buildMap<String, AnalyticsParamValue> {
            put("outcome", AnalyticsParamValue.StringValue(if (success) "success" else "failure"))
            put("duration_bucket", AnalyticsParamValue.StringValue(durationBucket(durationMs)))
            put("retry_bucket", AnalyticsParamValue.StringValue(retryBucket(retryCount)))
            failureCode?.let { put("failure_code", AnalyticsParamValue.StringValue(it)) }
        }
        analyticsClient?.log(AnalyticsEvent(name = EVENT_POST_UPLOAD_RESULT, params = params))
    }

    /** Never breaks post creation even if Crashlytics itself throws (e.g. not initialized). */
    private fun reportCompressionFailureNonFatal(cause: Throwable) {
        try {
            FirebaseCrashlytics.getInstance().recordException(ImageCompressionFailedException(cause))
        } catch (_: Exception) {
        }
    }

    private fun createNewPost(state: ImageUploadUiState) {
        val imageUri = state.imageUri
        val model = state.selectedModel
        if (imageUri == null || model == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true) }
            analyticsClient?.log(AnalyticsEvent(name = EVENT_POST_CREATE_START))

            val cropTransform = state.cropTransform?.toCropTransformOrNull()
            val compressed = runCatching {
                if (cropTransform != null) {
                    imageCompressor.compressWithCrop(imageUri, ImageCompressor.CarParams, cropTransform)
                } else {
                    imageCompressor.compress(imageUri, ImageCompressor.CarParams)
                }
            }.getOrElse { e ->
                logCompressResult(success = false)
                reportCompressionFailureNonFatal(e)
                _uiState.update {
                    it.copy(isPosting = false, userMessage = "Couldn't process the image. Please try again.")
                }
                return@launch
            }
            logCompressResult(success = true)

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

            val uploadStartedAt = System.currentTimeMillis()
            val attemptRetryCount = createPostRetryCount
            when (val result = postRepository.createPost(metadata, compressed.bytes, compressed.mimeType)) {
                is ApiResult.Success -> {
                    logUploadResult(success = true, durationMs = System.currentTimeMillis() - uploadStartedAt, retryCount = attemptRetryCount)
                    _uiState.update { it.copy(isPosting = false, postSuccess = true) }
                    postCreationSignal.emit(
                        PostCreatedEvent(
                            postId = result.data.postId,
                            uploadDurationMs = System.currentTimeMillis() - uploadStartedAt,
                            retryCount = createPostRetryCount,
                            lastErrorCode = lastCreatePostErrorCode,
                        )
                    )
                    createPostRetryCount = 0
                    lastCreatePostErrorCode = null
                }
                is ApiResult.Error -> {
                    val failureCode = if (result.isNetworkError) ERROR_CODE_NETWORK else result.code ?: "unrecognized"
                    logUploadResult(
                        success = false,
                        durationMs = System.currentTimeMillis() - uploadStartedAt,
                        retryCount = attemptRetryCount,
                        failureCode = failureCode,
                    )
                    createPostRetryCount++
                    lastCreatePostErrorCode = result.code
                    _uiState.update {
                        it.copy(isPosting = false, userMessage = result.message)
                    }
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
