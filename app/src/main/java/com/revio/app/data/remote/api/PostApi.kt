package com.revio.app.data.remote.api

import com.revio.app.data.model.Post
import com.revio.app.data.remote.dto.post.CreatePostResponse
import com.revio.app.data.remote.dto.post.FeedResponse
import com.revio.app.data.remote.dto.post.PostEditRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import java.util.*

interface PostApi {

    /**
     * Create a post. Multipart: a JSON `metadata` part ([com.revio.app.data.remote.dto.post.CreatePostMetadata])
     * plus the `image` file part. Returns 201 with the new post id.
     */
    @Multipart
    @POST("posts")
    suspend fun createPost(
        @Part("metadata") metadata: RequestBody,
        @Part image: MultipartBody.Part,
    ): Response<CreatePostResponse>

    @GET("posts/{postId}")
    suspend fun getPostById(
        @Path("postId") postId: UUID
    ): Response<Post>

    @GET("posts")
    suspend fun getAllPosts(): Response<List<Post>>

    @GET("posts/current-day")
    suspend fun getCurrentDayPostsForUser(
        @Header("Time-Zone") timeZone: String = TimeZone.getDefault().id
    ): Response<List<Post>>

    @PUT("posts/{postId}")
    suspend fun editPost(
        @Path("postId") postId: UUID,
        @Body request: PostEditRequest
    ): Response<Unit>

    @DELETE("posts/{postId}")
    suspend fun deletePost(
        @Path("postId") postId: UUID
    ): Response<Unit>

    /**
     * Cursor-based feed. The first page omits the cursor; subsequent pages pass the
     * `nextCursor` returned by the previous page as ISO-8601 timestamp + post id.
     */
    @GET("posts/feed")
    suspend fun getFeedPosts(
        @Query("limit") limit: Int,
        @Query("cursorCreatedAt") cursorCreatedAt: String? = null,
        @Query("cursorPostId") cursorPostId: String? = null
    ): Response<FeedResponse>

    /** Cursor-based paginated posts for a specific user. Same cursor shape as the feed. */
    @GET("users/{userId}/posts")
    suspend fun getUserPosts(
        @Path("userId") userId: UUID,
        @Query("limit") limit: Int,
        @Query("cursorCreatedAt") cursorCreatedAt: String? = null,
        @Query("cursorPostId") cursorPostId: String? = null,
    ): Response<FeedResponse>
}
