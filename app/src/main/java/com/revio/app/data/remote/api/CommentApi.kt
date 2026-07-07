package com.revio.app.data.remote.api

import com.revio.app.data.remote.dto.comment.CommentDto
import com.revio.app.data.remote.dto.comment.CommentRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.UUID

interface CommentApi {

    /** Comments for a post, oldest-first (as ordered by the server). */
    @GET("posts/{postId}/comments")
    suspend fun getCommentsForPost(
        @Path("postId") postId: UUID
    ): Response<List<CommentDto>>

    /** Add a comment to a post; returns the created comment (with author info) on 201. */
    @POST("posts/{postId}/comments")
    suspend fun addComment(
        @Path("postId") postId: UUID,
        @Body request: CommentRequest,
    ): Response<CommentDto>

    @DELETE("comments/{commentId}")
    suspend fun deleteComment(
        @Path("commentId") commentId: UUID
    ): Response<Unit>
}
