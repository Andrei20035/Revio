package com.revio.app.data.remote.dto.post

import kotlinx.serialization.Serializable

/** Server response for `POST /posts`: the id of the newly created post. */
@Serializable
data class CreatePostResponse(
    val postId: String,
)
