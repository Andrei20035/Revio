package com.revio.app.data.repository

import com.revio.app.data.remote.api.PostApi
import com.revio.app.data.remote.dto.post.CreatePostMetadata
import com.revio.app.data.remote.dto.post.FeedCursor
import com.revio.app.data.remote.dto.post.FeedResult
import com.revio.app.data.remote.dto.post.UpdatePostRequest
import com.revio.app.data.remote.dto.post.toDomain
import com.revio.app.data.model.FeedPost
import com.revio.app.data.model.Post
import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.safeApiCall
import com.revio.app.core.network.safeApiCallNoContent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

interface PostRepository {
    /** Creates a post via multipart upload (JSON metadata part + image bytes part). */
    suspend fun createPost(
        metadata: CreatePostMetadata,
        imageBytes: ByteArray,
        mimeType: String,
    ): ApiResult<Unit>
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
    private val postApi: PostApi
) : PostRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createPost(
        metadata: CreatePostMetadata,
        imageBytes: ByteArray,
        mimeType: String,
    ): ApiResult<Unit> {
        val metadataPart = json.encodeToString(metadata)
            .toRequestBody("application/json".toMediaType())
        val imagePart = MultipartBody.Part.createFormData(
            name = "image",
            filename = "post.jpg",
            body = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull()),
        )
        return when (val result = safeApiCall { postApi.createPost(metadataPart, imagePart) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getPostDetail(postId: UUID): ApiResult<FeedPost> {
        return when (val result = safeApiCall { postApi.getPostById(postId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getAllPosts(): ApiResult<List<Post>> {
        return when (val result = safeApiCall { postApi.getAllPosts()}) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getCurrentDayPostsForUser(): ApiResult<List<Post>> {
        val timeZone = TimeZone.getDefault().id
        return when (val result = safeApiCall { postApi.getCurrentDayPostsForUser(timeZone) }) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun updatePost(postId: UUID, request: UpdatePostRequest): ApiResult<FeedPost> {
        return when (val result = safeApiCall { postApi.updatePost(postId, request) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun deletePost(postId: UUID): ApiResult<Unit> {
        return safeApiCallNoContent { postApi.deletePost(postId) }
    }

    override suspend fun getFeedPosts(limit: Int, cursor: FeedCursor?): ApiResult<FeedResult> {
        return when (
            val result = safeApiCall {
                postApi.getFeedPosts(
                    limit = limit,
                    cursorCreatedAt = cursor?.lastCreatedAt?.toString(),
                    cursorPostId = cursor?.lastPostId?.toString()
                )
            }
        ) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getUserPosts(userId: UUID, limit: Int, cursor: FeedCursor?): ApiResult<FeedResult> {
        return when (
            val result = safeApiCall {
                postApi.getUserPosts(
                    userId = userId,
                    limit = limit,
                    cursorCreatedAt = cursor?.lastCreatedAt?.toString(),
                    cursorPostId = cursor?.lastPostId?.toString()
                )
            }
        ) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }
}
