package com.revio.social.features.upload

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.revio.social.MainDispatcherRule
import com.revio.social.core.feedback.PostCreatedEvent
import com.revio.social.core.feedback.PostCreationSignal
import com.revio.social.core.image.ImageCompressor
import com.revio.social.core.network.ApiResult
import com.revio.social.core.navigation.Screen
import com.revio.social.data.model.Coordinates
import com.revio.social.data.model.PlaceName
import com.revio.social.data.remote.dto.car_model.CarModelOption
import com.revio.social.data.remote.dto.post.CreatePostMetadata
import com.revio.social.data.repository.CarModelRepository
import com.revio.social.data.repository.CreatePostResult
import com.revio.social.data.repository.LocationRepository
import com.revio.social.data.repository.PostRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Covers the location-retry contract from the ImageUploadScreen location fix: retries are
 * allowed after a failed attempt (unlike the one-shot latch this replaced), concurrent
 * requests are prevented, and the failure reason (permission vs services vs no-fix) drives
 * [LocationStatus.Unavailable].
 */
class ImageUploadViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carModelRepository: CarModelRepository = mockk()
    private val postRepository: PostRepository = mockk()
    private val imageCompressor: ImageCompressor = mockk()
    private val locationRepository: LocationRepository = mockk()
    private val postCreationSignal: PostCreationSignal = mockk(relaxed = true)

    @Before
    fun setUp() {
        coEvery { carModelRepository.getAllCarBrands() } returns ApiResult.Success(emptyList())
        every { locationRepository.hasLocationPermission() } returns false
    }

    private fun viewModel(
        postId: String? = null,
        imageUri: String? = null,
    ): ImageUploadViewModel {
        val savedStateHandle = SavedStateHandle().apply {
            postId?.let { set(Screen.ImageUpload.ARG_POST_ID, it) }
            imageUri?.let { set(Screen.ImageUpload.ARG_IMAGE_URI, it) }
        }
        return ImageUploadViewModel(
            savedStateHandle = savedStateHandle,
            carModelRepository = carModelRepository,
            postRepository = postRepository,
            imageCompressor = imageCompressor,
            locationRepository = locationRepository,
            postCreationSignal = postCreationSignal,
        )
    }

    @Test
    fun `permission denied sets Unavailable PermissionDenied and never queries repository`() = runTest {
        val viewModel = viewModel()

        viewModel.onLocationPermissionResult(granted = false)

        val status = viewModel.uiState.value.locationStatus
        assertEquals(LocationStatus.Unavailable(LocationFailure.PermissionDenied), status)
        coVerify(exactly = 0) { locationRepository.getCurrentLocation() }
    }

    @Test
    fun `permission permanently denied sets Unavailable PermissionDeniedPermanently`() = runTest {
        val viewModel = viewModel()

        viewModel.onLocationPermissionResult(granted = false, permanentlyDenied = true)

        val status = viewModel.uiState.value.locationStatus
        assertEquals(LocationStatus.Unavailable(LocationFailure.PermissionDeniedPermanently), status)
    }

    @Test
    fun `granted with coordinates and place resolves to Resolved with town and country`() = runTest {
        coEvery { locationRepository.getCurrentLocation() } returns Coordinates(44.43, 26.10)
        coEvery { locationRepository.reverseGeocode(Coordinates(44.43, 26.10)) } returns
            PlaceName(town = "Bucharest", country = "Romania")
        val viewModel = viewModel()

        viewModel.onLocationPermissionResult(granted = true)

        val state = viewModel.uiState.value
        assertEquals(LocationStatus.Resolved, state.locationStatus)
        assertEquals(44.43, state.latitude)
        assertEquals(26.10, state.longitude)
        assertEquals("Bucharest", state.town)
        assertEquals("Romania", state.country)
    }

    @Test
    fun `granted with coordinates but no geocode result resolves to Resolved with null place`() = runTest {
        coEvery { locationRepository.getCurrentLocation() } returns Coordinates(44.43, 26.10)
        coEvery { locationRepository.reverseGeocode(any()) } returns null
        val viewModel = viewModel()

        viewModel.onLocationPermissionResult(granted = true)

        val state = viewModel.uiState.value
        assertEquals(LocationStatus.Resolved, state.locationStatus)
        assertNull(state.town)
        assertNull(state.country)
    }

    @Test
    fun `no fix with location services disabled sets Unavailable ServicesDisabled`() = runTest {
        coEvery { locationRepository.getCurrentLocation() } returns null
        every { locationRepository.locationServicesEnabled() } returns false
        val viewModel = viewModel()

        viewModel.onLocationPermissionResult(granted = true)

        val status = viewModel.uiState.value.locationStatus
        assertEquals(LocationStatus.Unavailable(LocationFailure.ServicesDisabled), status)
    }

    @Test
    fun `no fix with location services enabled sets Unavailable NoFix`() = runTest {
        coEvery { locationRepository.getCurrentLocation() } returns null
        every { locationRepository.locationServicesEnabled() } returns true
        val viewModel = viewModel()

        viewModel.onLocationPermissionResult(granted = true)

        val status = viewModel.uiState.value.locationStatus
        assertEquals(LocationStatus.Unavailable(LocationFailure.NoFix), status)
    }

    @Test
    fun `retry after a failed attempt resolves successfully and repository is queried twice`() = runTest {
        coEvery { locationRepository.getCurrentLocation() } returnsMany listOf(null, Coordinates(1.0, 2.0))
        every { locationRepository.locationServicesEnabled() } returns true
        coEvery { locationRepository.reverseGeocode(any()) } returns PlaceName(town = "Town", country = "Country")
        val viewModel = viewModel()

        viewModel.onLocationPermissionResult(granted = true)
        assertEquals(
            LocationStatus.Unavailable(LocationFailure.NoFix),
            viewModel.uiState.value.locationStatus,
        )

        viewModel.onRetryLocation()

        assertEquals(LocationStatus.Resolved, viewModel.uiState.value.locationStatus)
        coVerify(exactly = 2) { locationRepository.getCurrentLocation() }
    }

    @Test
    fun `retry while a request is already in flight is ignored`() = runTest {
        val deferred = CompletableDeferred<Coordinates?>()
        coEvery { locationRepository.getCurrentLocation() } coAnswers { deferred.await() }
        val viewModel = viewModel()

        viewModel.onLocationPermissionResult(granted = true)
        assertEquals(LocationStatus.Resolving, viewModel.uiState.value.locationStatus)

        viewModel.onRetryLocation()
        viewModel.onRetryLocation()

        every { locationRepository.locationServicesEnabled() } returns true
        deferred.complete(null)

        coVerify(exactly = 1) { locationRepository.getCurrentLocation() }
    }

    @Test
    fun `edit mode never queries the location repository`() = runTest {
        coEvery { postRepository.getPostDetail(any()) } returns
            ApiResult.Error("not used in this test")
        val viewModel = viewModel(postId = "5f4d6c2a-6b1f-4e34-8e2a-9f0e4a2f7f2a")

        viewModel.onLocationPermissionResult(granted = true)
        viewModel.onRetryLocation()

        coVerify(exactly = 0) { locationRepository.getCurrentLocation() }
        assertEquals(LocationStatus.Idle, viewModel.uiState.value.locationStatus)
    }

    @Test
    fun `posting while location is still resolving sends null coordinates and succeeds`() = runTest {
        mockkStatic(Uri::class)
        try {
            val fakeUri = mockk<Uri>()
            every { Uri.parse(any()) } returns fakeUri

            val deferred = CompletableDeferred<Coordinates?>()
            coEvery { locationRepository.getCurrentLocation() } coAnswers { deferred.await() }
            coEvery { imageCompressor.compress(fakeUri, any()) } returns
                ImageCompressor.CompressedImage(byteArrayOf(1, 2, 3), "image/jpeg")
            val metadataSlot = slot<CreatePostMetadata>()
            coEvery { postRepository.createPost(capture(metadataSlot), any(), any()) } returns
                ApiResult.Success(CreatePostResult(postId = UUID.randomUUID(), user = null))

            val brand = "BMW"
            val model = CarModelOption(id = UUID.randomUUID(), model = "M3")
            coEvery { carModelRepository.getModelsForBrand(brand) } returns ApiResult.Success(listOf(model))

            val viewModel = viewModel(imageUri = "content://fake/image.jpg")
            viewModel.onLocationPermissionResult(granted = true)
            assertEquals(LocationStatus.Resolving, viewModel.uiState.value.locationStatus)

            viewModel.onBrandSelected(brand)
            viewModel.onModelSelected(model.model)
            assertEquals(true, viewModel.uiState.value.canPost)

            viewModel.post()

            assertEquals(true, viewModel.uiState.value.postSuccess)
            coVerify { postRepository.createPost(any(), any(), any()) }
            assertNull(metadataSlot.captured.latitude)
            assertNull(metadataSlot.captured.longitude)
        } finally {
            unmockkStatic(Uri::class)
        }
    }

    @Test
    fun `creating a new post emits PostCreatedEvent with the returned postId`() = runTest {
        mockkStatic(Uri::class)
        try {
            val fakeUri = mockk<Uri>()
            every { Uri.parse(any()) } returns fakeUri
            coEvery { locationRepository.getCurrentLocation() } returns null
            every { locationRepository.locationServicesEnabled() } returns true
            coEvery { imageCompressor.compress(fakeUri, any()) } returns
                ImageCompressor.CompressedImage(byteArrayOf(1, 2, 3), "image/jpeg")

            val createdPostId = UUID.randomUUID()
            coEvery { postRepository.createPost(any(), any(), any()) } returns
                ApiResult.Success(CreatePostResult(postId = createdPostId, user = null))

            val brand = "BMW"
            val model = CarModelOption(id = UUID.randomUUID(), model = "M3")
            coEvery { carModelRepository.getModelsForBrand(brand) } returns ApiResult.Success(listOf(model))

            val viewModel = viewModel(imageUri = "content://fake/image.jpg")
            viewModel.onBrandSelected(brand)
            viewModel.onModelSelected(model.model)

            viewModel.post()

            assertEquals(true, viewModel.uiState.value.postSuccess)
            val eventSlot = slot<PostCreatedEvent>()
            coVerify(exactly = 1) { postCreationSignal.emit(capture(eventSlot)) }
            assertEquals(createdPostId, eventSlot.captured.postId)
        } finally {
            unmockkStatic(Uri::class)
        }
    }

    @Test
    fun `a failed create-post attempt does not emit PostCreatedEvent`() = runTest {
        mockkStatic(Uri::class)
        try {
            val fakeUri = mockk<Uri>()
            every { Uri.parse(any()) } returns fakeUri
            coEvery { locationRepository.getCurrentLocation() } returns null
            every { locationRepository.locationServicesEnabled() } returns true
            coEvery { imageCompressor.compress(fakeUri, any()) } returns
                ImageCompressor.CompressedImage(byteArrayOf(1, 2, 3), "image/jpeg")

            coEvery { postRepository.createPost(any(), any(), any()) } returns
                ApiResult.Error("server error")

            val brand = "BMW"
            val model = CarModelOption(id = UUID.randomUUID(), model = "M3")
            coEvery { carModelRepository.getModelsForBrand(brand) } returns ApiResult.Success(listOf(model))

            val viewModel = viewModel(imageUri = "content://fake/image.jpg")
            viewModel.onBrandSelected(brand)
            viewModel.onModelSelected(model.model)

            viewModel.post()

            assertEquals(false, viewModel.uiState.value.postSuccess)
            coVerify(exactly = 0) { postCreationSignal.emit(any()) }
        } finally {
            unmockkStatic(Uri::class)
        }
    }

    @Test
    fun `editing an existing post never emits PostCreatedEvent`() = runTest {
        val postId = UUID.randomUUID()
        coEvery { postRepository.getPostDetail(postId) } returns ApiResult.Error("not used in this test")
        val model = CarModelOption(id = UUID.randomUUID(), model = "M3")
        coEvery { carModelRepository.getModelsForBrand("BMW") } returns ApiResult.Success(listOf(model))
        coEvery { postRepository.updatePost(eq(postId), any()) } returns
            ApiResult.Success(mockk(relaxed = true))

        val viewModel = viewModel(postId = postId.toString())
        viewModel.onBrandSelected("BMW")
        viewModel.onModelSelected(model.model)

        viewModel.post()

        coVerify(exactly = 0) { postCreationSignal.emit(any()) }
    }
}
