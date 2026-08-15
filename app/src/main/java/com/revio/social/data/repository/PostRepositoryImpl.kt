package com.revio.social.data.repository

import com.revio.social.data.remote.api.PostApi
import com.revio.social.data.remote.dto.post.CreatePostMetadata
import com.revio.social.data.remote.dto.post.FeedCursor
import com.revio.social.data.remote.dto.post.FeedResult
import com.revio.social.data.remote.dto.post.UpdatePostRequest
import com.revio.social.data.remote.dto.post.toDomain
import com.revio.social.data.model.FeedPost
import com.revio.social.data.model.Post
import com.revio.social.data.model.User
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.map
import com.revio.social.core.network.safeApiCall
import com.revio.social.core.network.safeApiCallNoContent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a successful post creation: the new post's id and the (possibly updated) current user. */
data class CreatePostResult(
    val postId: UUID,
    val user: User?,
)

/** Server error code for PATCH /posts/{postId} when the post's vehicle is locked (see server PostRoutes.kt). */
private const val VEHICLE_LOCKED_ERROR_CODE = "CHALLENGE_POST_VEHICLE_LOCKED"

interface PostRepository {
    /** Creates a post via multipart upload (JSON metadata part + image bytes part). */
    suspend fun createPost(
        metadata: CreatePostMetadata,
        imageBytes: ByteArray,
        mimeType: String,
    ): ApiResult<CreatePostResult>
    suspend fun getPostDetail(postId: UUID): ApiResult<FeedPost>
    suspend fun getAllPosts(): ApiResult<List<Post>>
    suspend fun getCurrentDayPostsForUser(): ApiResult<List<Post>>
    suspend fun updatePost(postId: UUID, request: UpdatePostRequest): ApiResult<FeedPost>
    suspend fun deletePost(postId: UUID): ApiResult<Unit>
    suspend fun getFeedPosts(limit: Int, cursor: FeedCursor? = null): ApiResult<FeedResult>
    suspend fun getUserPosts(userId: UUID, limit: Int, cursor: FeedCursor? = null): ApiResult<FeedResult>
}
@Singleton
class PostRepositoryImpl @Inject constructor(
    private val postApi: PostApi,
    private val userRepository: UserRepository,
) : PostRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createPost(
        metadata: CreatePostMetadata,
        imageBytes: ByteArray,
        mimeType: String,
    ): ApiResult<CreatePostResult> {
        val metadataPart = json.encodeToString(metadata)
            .toRequestBody("application/json".toMediaType())
        val imagePart = MultipartBody.Part.createFormData(
            name = "image",
            filename = "post.jpg",
            body = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull()),
        )
        return safeApiCall { postApi.createPost(metadataPart, imagePart) }
            .map { response ->
                response.user?.let { userRepository.setCurrentUser(it) }
                CreatePostResult(postId = UUID.fromString(response.postId), user = response.user)
            }
    }

    override suspend fun getPostDetail(postId: UUID): ApiResult<FeedPost> {
        return safeApiCall { postApi.getPostById(postId) }.map { it.toDomain() }
    }

    override suspend fun getAllPosts(): ApiResult<List<Post>> {
        return safeApiCall { postApi.getAllPosts() }.map { it }
    }

    override suspend fun getCurrentDayPostsForUser(): ApiResult<List<Post>> {
        val timeZone = TimeZone.getDefault().id
        return safeApiCall { postApi.getCurrentDayPostsForUser(timeZone) }.map { it }
    }

    override suspend fun updatePost(postId: UUID, request: UpdatePostRequest): ApiResult<FeedPost> {
        return when (val result = safeApiCall { postApi.updatePost(postId, request) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> if (result.code == VEHICLE_LOCKED_ERROR_CODE) {
                ApiResult.Error(
                    message = "This post's brand and model can no longer be changed because it has contributed to a challenge.",
                    code = result.code,
                )
            } else {
                result
            }
        }
    }

    override suspend fun deletePost(postId: UUID): ApiResult<Unit> {
        return safeApiCallNoContent { postApi.deletePost(postId) }
    }

    override suspend fun getFeedPosts(limit: Int, cursor: FeedCursor?): ApiResult<FeedResult> {
        return safeApiCall {
            postApi.getFeedPosts(
                limit = limit,
                cursorCreatedAt = cursor?.lastCreatedAt?.toString(),
                cursorPostId = cursor?.lastPostId?.toString()
            )
        }.map { it.toDomain() }
    }

    override suspend fun getUserPosts(userId: UUID, limit: Int, cursor: FeedCursor?): ApiResult<FeedResult> {
        return safeApiCall {
            postApi.getUserPosts(
                userId = userId,
                limit = limit,
                cursorCreatedAt = cursor?.lastCreatedAt?.toString(),
                cursorPostId = cursor?.lastPostId?.toString()
            )
        }.map { it.toDomain() }
    }
}
