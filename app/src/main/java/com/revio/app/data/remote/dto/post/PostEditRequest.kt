package com.revio.app.data.remote.dto.post

import kotlinx.serialization.Serializable

@Serializable
data class PostEditRequest(
    val newDescription: String? = null,
)