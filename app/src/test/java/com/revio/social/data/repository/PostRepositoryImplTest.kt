package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.isNetworkError
import com.revio.social.data.model.User
import com.revio.social.data.remote.api.PostApi
import com.revio.social.data.remote.dto.post.CreatePostMetadata
import com.revio.social.data.remote.dto.post.CreatePostResponse
import com.revio.social.data.remote.dto.post.UpdatePostRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import io.mockk.verify

/**
 * Regression guard for the fix that stopped [PostRepositoryImpl] from losing [ApiResult.Error.code]
 * on the feed path — before it, `.getFeedPosts` rebuilt a bare `ApiResult.Error(message)` on the
 * success->domain mapping instead of piping the original error through, so `isNetworkError` was
 * always false and the feed could never tell "offline" apart from a server error.
 */
class PostRepositoryImplTest {

    private lateinit var postApi: PostApi
    private lateinit var userRepository: UserRepository
    private lateinit var repository: PostRepositoryImpl

    @Before
    fun setup() {
        postApi = mockk()
        userRepository = mockk(relaxed = true)
        repository = PostRepositoryImpl(postApi, userRepository)
    }

    @Test
    fun `getFeedPosts pastreaza codul de eroare de retea`() = runTest {
        coEvery { postApi.getFeedPosts(limit = 15, cursorCreatedAt = null, cursorPostId = null) } throws IOException()

        val result = repository.getFeedPosts(limit = 15, cursor = null)

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).isNetworkError)
    }

    @Test
    fun `getUserPosts pastreaza codul de eroare de retea`() = runTest {
        val userId = java.util.UUID.randomUUID()
        coEvery {
            postApi.getUserPosts(userId = userId, limit = 15, cursorCreatedAt = null, cursorPostId = null)
        } throws IOException()

        val result = repository.getUserPosts(userId = userId, limit = 15, cursor = null)

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).isNetworkError)
    }

    @Test
    fun `createPost cu user in raspuns apeleaza setCurrentUser exact o data cu userul din raspuns`() = runTest {
        val returnedUser = User(
            id = UUID.randomUUID(),
            fullName = "Current User",
            username = "current_user",
            country = "Romania",
            spotScore = 42,
            postCount = 3,
            streakDays = 2,
        )
        coEvery { postApi.createPost(any(), any()) } returns
            Response.success(CreatePostResponse(postId = UUID.randomUUID().toString(), user = returnedUser))

        repository.createPost(
            metadata = CreatePostMetadata(),
            imageBytes = ByteArray(0),
            mimeType = "image/jpeg",
        )

        coVerify(exactly = 1) { userRepository.setCurrentUser(returnedUser) }
    }

    @Test
    fun `createPost cu user null nu apeleaza setCurrentUser si rezultatul ramane Success`() = runTest {
        coEvery { postApi.createPost(any(), any()) } returns
            Response.success(CreatePostResponse(postId = UUID.randomUUID().toString(), user = null))

        val result = repository.createPost(
            metadata = CreatePostMetadata(),
            imageBytes = ByteArray(0),
            mimeType = "image/jpeg",
        )

        verify(exactly = 0) { userRepository.setCurrentUser(any()) }
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `createPost esuat cu IOException nu apeleaza setCurrentUser si rezultatul este Error`() = runTest {
        coEvery { postApi.createPost(any(), any()) } throws IOException()

        val result = repository.createPost(
            metadata = CreatePostMetadata(),
            imageBytes = ByteArray(0),
            mimeType = "image/jpeg",
        )

        verify(exactly = 0) { userRepository.setCurrentUser(any()) }
        assertTrue(result is ApiResult.Error)
    }

    // ---- updatePost 409 CHALLENGE_POST_VEHICLE_LOCKED mapping ----

    @Test
    fun `updatePost cu 409 CHALLENGE_POST_VEHICLE_LOCKED mapeaza la un mesaj explicativ dedicat`() = runTest {
        val postId = UUID.randomUUID()
        val errorBody =
            """{"error":"This post's vehicle can no longer be changed because it has contributed to a challenge","code":"CHALLENGE_POST_VEHICLE_LOCKED"}"""
        coEvery { postApi.updatePost(postId, any()) } returns
            Response.error(409, errorBody.toResponseBody("application/json".toMediaType()))

        val result = repository.updatePost(postId, UpdatePostRequest(carModelId = UUID.randomUUID()))

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals("CHALLENGE_POST_VEHICLE_LOCKED", error.code)
        assertEquals(
            "This post's brand and model can no longer be changed because it has contributed to a challenge.",
            error.message,
        )
    }

    @Test
    fun `updatePost cu alt cod de eroare nu este rescris - mesajul serverului trece neschimbat`() = runTest {
        val postId = UUID.randomUUID()
        val errorBody = """{"error":"Post not found"}"""
        coEvery { postApi.updatePost(postId, any()) } returns
            Response.error(404, errorBody.toResponseBody("application/json".toMediaType()))

        val result = repository.updatePost(postId, UpdatePostRequest(carModelId = UUID.randomUUID()))

        assertTrue(result is ApiResult.Error)
        assertEquals("Post not found", (result as ApiResult.Error).message)
    }

    @Test
    fun `updatePost cu succes converteste raspunsul in domeniu`() = runTest {
        val postId = UUID.randomUUID()
        val dto = com.revio.social.data.remote.dto.post.FeedPostDto(
            id = postId,
            userId = UUID.randomUUID(),
            username = "owner",
            brand = "Audi",
            model = "RS6",
            imageUrl = "https://example.com/post.jpg",
            createdAt = java.time.Instant.now(),
        )
        coEvery { postApi.updatePost(postId, any()) } returns Response.success(dto)

        val result = repository.updatePost(postId, UpdatePostRequest(carModelId = UUID.randomUUID()))

        assertTrue(result is ApiResult.Success)
        assertEquals("Audi", (result as ApiResult.Success).data.brand)
    }
}
