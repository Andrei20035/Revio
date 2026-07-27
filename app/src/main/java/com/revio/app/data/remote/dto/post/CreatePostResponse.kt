package com.revio.app.data.remote.dto.post

import com.revio.app.data.model.User
import kotlinx.serialization.Serializable

/** Server response for `POST /posts`: the id of the newly created post. */
@Serializable
data class CreatePostResponse(
    val postId: String,
    val user: User? = null,
)
