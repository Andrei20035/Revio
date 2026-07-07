package com.revio.app.data.remote.dto.comment

import com.revio.app.core.network.serialization.InstantSerializer
import com.revio.app.core.network.serialization.UUIDSerializer
import com.revio.app.data.model.Comment
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Network shape of the server `CommentDTO` returned by the comments endpoints. Carries the
 * author's username and profile picture so the overlay can render each row without extra lookups.
 */
@Serializable
data class CommentDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val postId: UUID,
    val username: String,
    val profilePicturePath: String? = null,
    val commentText: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant? = null,
)

fun CommentDto.toDomain(): Comment = Comment(
    id = id,
    userId = userId,
    postId = postId,
    username = username,
    profilePictureUrl = profilePicturePath,
    text = commentText,
    createdAt = createdAt,
)
