package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.safeApiCall
import com.revio.social.data.model.Comment
import com.revio.social.data.remote.api.CommentApi
import com.revio.social.data.remote.dto.comment.CommentRequest
import com.revio.social.data.remote.dto.comment.toDomain
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface CommentRepository {
    suspend fun getCommentsForPost(postId: UUID): ApiResult<List<Comment>>
    suspend fun addComment(postId: UUID, text: String): ApiResult<Comment>
    suspend fun deleteComment(commentId: UUID): ApiResult<Unit>
}

@Singleton
class CommentRepositoryImpl @Inject constructor(
    private val commentApi: CommentApi
) : CommentRepository {

    override suspend fun getCommentsForPost(postId: UUID): ApiResult<List<Comment>> {
        return when (val result = safeApiCall { commentApi.getCommentsForPost(postId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.map { it.toDomain() })
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun addComment(postId: UUID, text: String): ApiResult<Comment> {
        return when (val result = safeApiCall { commentApi.addComment(postId, CommentRequest(text)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun deleteComment(commentId: UUID): ApiResult<Unit> {
        return safeApiCall { commentApi.deleteComment(commentId) }
    }
}
