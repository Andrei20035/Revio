package com.revio.app.data.remote.dto.post

import com.revio.app.data.model.FeedPost

/** Domain result of a feed page: the items plus the cursor for the next page. */
data class FeedResult(
    val posts: List<FeedPost>,
    val nextCursor: FeedCursor?,
    val hasMore: Boolean
)

fun FeedResponse.toDomain(): FeedResult = FeedResult(
    posts = posts.map { it.toDomain() },
    nextCursor = nextCursor,
    hasMore = hasMore
)
