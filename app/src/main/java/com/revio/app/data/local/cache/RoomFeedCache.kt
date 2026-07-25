package com.revio.app.data.local.cache

import com.revio.app.data.local.db.feed.FeedDao
import com.revio.app.data.local.db.feed.FeedMetaEntity
import com.revio.app.data.local.db.feed.toDomain
import com.revio.app.data.local.db.feed.toEntity
import com.revio.app.data.model.FeedPost
import com.revio.app.data.remote.dto.post.FeedCursor
import com.revio.app.data.remote.dto.post.FeedResult
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomFeedCache @Inject constructor(
    private val feedDao: FeedDao,
) : FeedCache {

    override fun observePosts(): Flow<List<FeedPost>> =
        feedDao.observePosts().map { entities -> entities.map { it.toDomain() } }

    override suspend fun readMeta(): FeedCacheMeta? = feedDao.getMeta()?.toCacheMeta()

    override suspend fun replaceWithFirstPage(page: FeedResult, ownerUserId: UUID?, syncedAt: Instant) {
        val entities = page.posts.mapIndexed { index, post -> post.toEntity(position = index) }
        val meta = FeedMetaEntity(
            nextCursorCreatedAt = page.nextCursor?.lastCreatedAt?.toString(),
            nextCursorPostId = page.nextCursor?.lastPostId?.toString(),
            hasMore = page.hasMore,
            lastSyncedAtEpochMs = syncedAt.toEpochMilli(),
            ownerUserId = ownerUserId?.toString(),
            maxPosition = entities.size - 1,
        )
        feedDao.replaceWithFirstPage(entities, meta)
    }

    override suspend fun appendPage(page: FeedResult, syncedAt: Instant) {
        val existingMeta = feedDao.getMeta()
        // Position is assigned inside FeedDao.appendPage (existing ids keep their position, new
        // ones go past the current max), so the value passed in here is only a placeholder.
        val entities = page.posts.map { it.toEntity(position = 0) }
        val meta = FeedMetaEntity(
            nextCursorCreatedAt = page.nextCursor?.lastCreatedAt?.toString(),
            nextCursorPostId = page.nextCursor?.lastPostId?.toString(),
            hasMore = page.hasMore,
            lastSyncedAtEpochMs = syncedAt.toEpochMilli(),
            ownerUserId = existingMeta?.ownerUserId,
            // Advisory only — the DAO always recomputes the true max live via MAX(position);
            // nothing reads this column for correctness.
            maxPosition = feedDao.maxPosition() ?: -1,
        )
        feedDao.appendPage(entities, meta)
    }

    override suspend fun updateLike(postId: UUID, liked: Boolean, likeCount: Long) {
        feedDao.updateLike(postId.toString(), liked, likeCount)
    }

    override suspend fun setCommentCount(postId: UUID, count: Long) {
        feedDao.setCommentCount(postId.toString(), count)
    }

    override suspend fun trimTo(maxPosts: Int) = feedDao.trimTo(maxPosts)

    override suspend fun clear() = feedDao.clearAll()
}

private fun FeedMetaEntity.toCacheMeta(): FeedCacheMeta = FeedCacheMeta(
    nextCursor = if (nextCursorCreatedAt != null && nextCursorPostId != null) {
        FeedCursor(
            lastCreatedAt = Instant.parse(nextCursorCreatedAt),
            lastPostId = UUID.fromString(nextCursorPostId),
        )
    } else {
        null
    },
    hasMore = hasMore,
    lastSyncedAt = Instant.ofEpochMilli(lastSyncedAtEpochMs),
    ownerUserId = ownerUserId?.let { UUID.fromString(it) },
)
