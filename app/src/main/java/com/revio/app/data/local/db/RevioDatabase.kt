package com.revio.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.revio.app.data.local.db.feed.FeedDao
import com.revio.app.data.local.db.feed.FeedMetaEntity
import com.revio.app.data.local.db.feed.FeedPostEntity

@Database(
    entities = [FeedPostEntity::class, FeedMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RevioDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
}
