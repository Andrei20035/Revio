package com.revio.app.data.local.cache

import com.revio.app.data.model.FeedPost
import com.revio.app.data.remote.dto.post.FeedCursor
import com.revio.app.data.remote.dto.post.FeedResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Persistent cache for the feed. Implementations are the only place that knows about local
 * storage — callers (repository/ViewModel) see domain types only, never Room entities, so the
 * cache can be swapped or unit-tested with a fake with no changes above this interface.
 */
interface FeedCache {

    /** Emits the cached feed, ordered the same way it's meant to render. */
    fun observePosts(): Flow<List<FeedPost>>

    /** Pagination cursor and bookkeeping for the cached feed, or null if nothing is cached. */
    suspend fun readMeta(): FeedCacheMeta?

    /** Delete-and-replace with a first page; call only after a successful network response. */
    suspend fun replaceWithFirstPage(page: FeedResult, ownerUserId: UUID?, syncedAt: Instant)

    /** Appends a page; call only after a successful network response. */
    suspend fun appendPage(page: FeedResult, syncedAt: Instant)

    /** Advances the freshness timestamp without touching posts or pagination; for a silent sync that returned zero posts. */
    suspend fun markSynced(syncedAt: Instant)

    /** Writes a like toggle's outcome (optimistic or server-confirmed) into the cached post. */
    suspend fun updateLike(postId: UUID, liked: Boolean, likeCount: Long)

    /** Writes a new comment count into the cached post. */
    suspend fun setCommentCount(postId: UUID, count: Long)

    /** Keeps only the earliest [maxPosts] cached posts; intended for cold-start hydration only. */
    suspend fun trimTo(maxPosts: Int)

    /** Wipes the cache; call on logout/session expiry or on an owner mismatch. */
    suspend fun clear()
}

data class FeedCacheMeta(
    val nextCursor: FeedCursor?,
    val hasMore: Boolean,
    val lastSyncedAt: Instant?,
    val ownerUserId: UUID?,
)
