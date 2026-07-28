package com.revio.social.data.remote.dto.post

import com.revio.social.data.model.User
import kotlinx.serialization.Serializable

/** Server response for `POST /posts`: the id of the newly created post. */
@Serializable
data class CreatePostResponse(
    val postId: String,
    val user: User? = null,
)
