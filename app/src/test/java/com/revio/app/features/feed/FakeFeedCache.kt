package com.revio.app.features.feed

import com.revio.app.data.local.cache.FeedCache
import com.revio.app.data.local.cache.FeedCacheMeta
import com.revio.app.data.model.FeedPost
import com.revio.app.data.remote.dto.post.FeedResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [FeedCache] double for JVM tests — no Room, no Robolectric. Mirrors
 * [com.revio.app.data.local.cache.RoomFeedCache]'s observable semantics closely enough for
 * [FeedViewModel] tests: a single [Flow] of posts ordered by insertion/append, and one mutable
 * meta row.
 */
class FakeFeedCache : FeedCache {

    private val postsFlow = MutableStateFlow<List<FeedPost>>(emptyList())
    var meta: FeedCacheMeta? = null

    /** Number of times [clear] was called — lets tests assert an owner-mismatch wipe happened. */
    var clearCount: Int = 0
        private set

    override fun observePosts() = postsFlow

    override suspend fun readMeta(): FeedCacheMeta? = meta

    override suspend fun replaceWithFirstPage(page: FeedResult, ownerUserId: UUID?, syncedAt: Instant) {
        postsFlow.value = page.posts
        meta = FeedCacheMeta(
            nextCursor = page.nextCursor,
            hasMore = page.hasMore,
            lastSyncedAt = syncedAt,
            ownerUserId = ownerUserId,
        )
    }

    override suspend fun appendPage(page: FeedResult, syncedAt: Instant) {
        val existingIds = postsFlow.value.map { it.id }.toSet()
        postsFlow.value = postsFlow.value + page.posts.filterNot { it.id in existingIds }
        meta = meta?.copy(
            nextCursor = page.nextCursor,
            hasMore = page.hasMore,
            lastSyncedAt = syncedAt,
        ) ?: FeedCacheMeta(
            nextCursor = page.nextCursor,
            hasMore = page.hasMore,
            lastSyncedAt = syncedAt,
            ownerUserId = null,
        )
    }

    override suspend fun updateLike(postId: UUID, liked: Boolean, likeCount: Long) {
        postsFlow.value = postsFlow.value.map { post ->
            if (post.id == postId) post.copy(likedByCurrentUser = liked, likeCount = likeCount) else post
        }
    }

    override suspend fun setCommentCount(postId: UUID, count: Long) {
        postsFlow.value = postsFlow.value.map { post ->
            if (post.id == postId) post.copy(commentCount = count) else post
        }
    }

    override suspend fun trimTo(maxPosts: Int) {
        postsFlow.value = postsFlow.value.take(maxPosts)
    }

    override suspend fun clear() {
        clearCount++
        postsFlow.value = emptyList()
        meta = null
    }
}
