package com.revio.app.di

import android.content.Context
import androidx.room.Room
import com.revio.app.data.local.db.RevioDatabase
import com.revio.app.data.local.db.feed.FeedDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRevioDatabase(@ApplicationContext context: Context): RevioDatabase {
        // Pure cache, rebuilt from the network on the next load — a destructive fallback is
        // the correct behavior for a schema bump, not a migration to write.
        return Room.databaseBuilder(context, RevioDatabase::class.java, "revio.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideFeedDao(database: RevioDatabase): FeedDao = database.feedDao()
}
