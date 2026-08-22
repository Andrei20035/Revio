package com.revio.social.data.local.db.feed

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    @Query("SELECT * FROM feed_posts ORDER BY position ASC")
    fun observePosts(): Flow<List<FeedPostEntity>>

    @Query("SELECT * FROM feed_meta WHERE id = 0")
    suspend fun getMeta(): FeedMetaEntity?

    /**
     * Delete-and-replace: only called on a successful first-page response. Runs as one
     * transaction so Room emits [observePosts] exactly once (no empty-list flicker) and a
     * process kill mid-refresh can never leave posts and cursor out of sync.
     */
    @Transaction
    suspend fun replaceWithFirstPage(posts: List<FeedPostEntity>, meta: FeedMetaEntity) {
        deleteAllPosts()
        insertPosts(posts)
        upsertMeta(meta)
    }

    /**
     * Appends a page, preserving the existing [FeedPostEntity.position] for any post that's
     * already cached (a new post shifting the server's page window can otherwise repeat an
     * id across two pages) and assigning fresh positions past the current max for the rest.
     */
    @Transaction
    suspend fun appendPage(posts: List<FeedPostEntity>, meta: FeedMetaEntity) {
        var nextPosition = (maxPosition() ?: -1) + 1
        val toInsert = posts.map { post ->
            val existingPosition = positionOf(post.id)
            if (existingPosition != null) {
                post.copy(position = existingPosition)
            } else {
                post.copy(position = nextPosition++)
            }
        }
        insertPosts(toInsert)
        upsertMeta(meta)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<FeedPostEntity>)

    @Query("SELECT position FROM feed_posts WHERE id = :id")
    suspend fun positionOf(id: String): Int?

    @Query("SELECT MAX(position) FROM feed_posts")
    suspend fun maxPosition(): Int?

    @Upsert
    suspend fun upsertMeta(meta: FeedMetaEntity)

    /** Advances the freshness timestamp without touching posts or pagination — for a silent sync that found nothing new. */
    @Query("UPDATE feed_meta SET lastSyncedAtEpochMs = :epochMs WHERE id = 0")
    suspend fun markSynced(epochMs: Long)

    @Query("UPDATE feed_posts SET likedByCurrentUser = :liked, likeCount = :likeCount WHERE id = :postId")
    suspend fun updateLike(postId: String, liked: Boolean, likeCount: Long)

    @Query("UPDATE feed_posts SET commentCount = :count WHERE id = :postId")
    suspend fun setCommentCount(postId: String, count: Long)

    /** Keeps the [maxPosts] lowest positions (the earliest-cached pages); drops the rest. */
    @Query("DELETE FROM feed_posts WHERE id NOT IN (SELECT id FROM feed_posts ORDER BY position ASC LIMIT :maxPosts)")
    suspend fun trimTo(maxPosts: Int)

    /** Removes a single post — used when an admin removes it server-side, independent of any page replace/append. */
    @Query("DELETE FROM feed_posts WHERE id = :id")
    suspend fun deletePost(id: String)

    @Query("DELETE FROM feed_posts")
    suspend fun deleteAllPosts()

    @Query("DELETE FROM feed_meta")
    suspend fun deleteMeta()

    @Transaction
    suspend fun clearAll() {
        deleteAllPosts()
        deleteMeta()
    }
}
