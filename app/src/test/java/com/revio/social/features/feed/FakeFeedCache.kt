package com.revio.social.features.feed

import com.revio.social.data.local.cache.FeedCache
import com.revio.social.data.local.cache.FeedCacheMeta
import com.revio.social.data.model.FeedPost
import com.revio.social.data.remote.dto.post.FeedResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * In-memory [FeedCache] double for JVM tests — no Room, no Robolectric. Mirrors
 * [com.revio.social.data.local.cache.RoomFeedCache]'s observable semantics closely enough for
 * [FeedViewModel] tests: a single [Flow] of posts ordered by insertion/append, and one mutable
 * meta row.
 *
 * [emissionScope]/[emissionDelayMs] optionally reproduce a real gap that a plain
 * `MutableStateFlow` otherwise hides: in production, a first-page write commits (the DAO
 * transaction/suspend call returns) *before* Room's `observePosts()` flow re-queries and emits
 * the new list on another dispatcher hop. When [emissionDelayMs] is > 0, writes still complete
 * synchronously (so callers see up-to-date [readMeta] immediately, matching Room), but the new
 * list is only published to [observePosts] after that delay, launched on [emissionScope] so it
 * advances with the test's virtual time.
 */
class FakeFeedCache(
    private val emissionScope: CoroutineScope? = null,
    private val emissionDelayMs: Long = 0,
) : FeedCache {

    private val postsFlow = MutableStateFlow<List<FeedPost>>(emptyList())
    var meta: FeedCacheMeta? = null

    /** Number of times [clear] was called — lets tests assert an owner-mismatch wipe happened. */
    var clearCount: Int = 0
        private set

    /** Number of times [markSynced] was called — lets tests assert a silent-empty sync only bumped freshness. */
    var markSyncedCount: Int = 0
        private set

    /**
     * When true, the next [readMeta] call throws instead of returning — simulates a corrupt/failing
     * cache read (e.g. first-ever Room DB creation) so [FeedViewModel.hydrateFromCache]'s recovery
     * path can be exercised. Resets itself after throwing once, so the recovery's own re-reads succeed.
     */
    var failNextReadMeta: Boolean = false

    override fun observePosts() = postsFlow

    override suspend fun readMeta(): FeedCacheMeta? {
        if (failNextReadMeta) {
            failNextReadMeta = false
            throw IllegalStateException("simulated cache read failure")
        }
        return meta
    }

    override suspend fun replaceWithFirstPage(page: FeedResult, ownerUserId: UUID?, syncedAt: Instant) {
        // meta must land before publishPosts(): a collector on observePosts() may react
        // synchronously to the emission below and call readMeta() from within that same reaction,
        // exactly like Room's real @Transaction commits meta and posts together before the Flow
        // re-queries — see the class doc's "matching Room" claim.
        meta = FeedCacheMeta(
            nextCursor = page.nextCursor,
            hasMore = page.hasMore,
            lastSyncedAt = syncedAt,
            ownerUserId = ownerUserId,
        )
        publishPosts(page.posts)
    }

    override suspend fun appendPage(page: FeedResult, syncedAt: Instant) {
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
        val existingIds = postsFlow.value.map { it.id }.toSet()
        publishPosts(postsFlow.value + page.posts.filterNot { it.id in existingIds })
    }

    /** Publishes synchronously unless an [emissionScope]+[emissionDelayMs] were configured. */
    private fun publishPosts(posts: List<FeedPost>) {
        if (emissionDelayMs > 0 && emissionScope != null) {
            emissionScope.launch {
                delay(emissionDelayMs)
                postsFlow.value = posts
            }
        } else {
            postsFlow.value = posts
        }
    }

    override suspend fun markSynced(syncedAt: Instant) {
        markSyncedCount++
        meta = meta?.copy(lastSyncedAt = syncedAt)
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

    override suspend fun deletePost(postId: UUID) {
        publishPosts(postsFlow.value.filterNot { it.id == postId })
    }

    override suspend fun clear() {
        clearCount++
        postsFlow.value = emptyList()
        meta = null
    }
}
