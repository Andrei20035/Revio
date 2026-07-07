package com.revio.app.data.remote.dto.post

import kotlinx.serialization.Serializable

@Serializable
data class FeedResponse(
    val posts: List<FeedPostDto>,
    val nextCursor: FeedCursor? = null,
    val hasMore: Boolean
)
