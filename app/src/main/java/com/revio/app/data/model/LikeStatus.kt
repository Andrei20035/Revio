package com.revio.app.data.model

/**
 * Authoritative like state for a post as returned by the backend after a toggle or status query.
 * [liked] is the current user's state; [count] is the post's total like count.
 */
data class LikeStatus(
    val liked: Boolean,
    val count: Long,
)
