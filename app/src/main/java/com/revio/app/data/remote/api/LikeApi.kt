package com.revio.app.data.remote.api

import com.revio.app.data.remote.dto.like.LikeStatusDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.UUID

interface LikeApi {

    /**
     * Toggle the current user's like on a post. The server flips the state and returns the
     * authoritative [LikeStatusDto] (new liked flag + total count).
     */
    @POST("posts/{postId}/likes")
    suspend fun toggleLike(
        @Path("postId") postId: UUID
    ): Response<LikeStatusDto>

    /** Current like state for a post (liked-by-current-user + total count). */
    @GET("posts/{postId}/likes")
    suspend fun getLikeStatus(
        @Path("postId") postId: UUID
    ): Response<LikeStatusDto>
}
