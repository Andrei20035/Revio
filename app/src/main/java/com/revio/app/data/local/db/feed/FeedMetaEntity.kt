package com.revio.app.data.local.db.feed

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table holding the feed's pagination cursor and cache bookkeeping. Lives in the
 * same database as [FeedPostEntity] (not DataStore) so a refresh can replace posts and cursor
 * atomically in one transaction.
 */
@Entity(tableName = "feed_meta")
data class FeedMetaEntity(
    @PrimaryKey val id: Int = 0,
    val nextCursorCreatedAt: String?,
    val nextCursorPostId: String?,
    val hasMore: Boolean,
    val lastSyncedAtEpochMs: Long,
    val ownerUserId: String?,
    val maxPosition: Int,
)
