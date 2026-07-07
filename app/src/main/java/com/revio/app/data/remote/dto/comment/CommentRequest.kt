package com.revio.app.data.remote.dto.comment

import kotlinx.serialization.Serializable

/**
 * Body for `POST posts/{postId}/comments`. The post id travels in the path and the author is
 * derived from the auth token server-side, so only the comment text is sent.
 */
@Serializable
data class CommentRequest(
    val commentText: String,
)
