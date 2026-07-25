package com.revio.app.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

private const val FEED_IMAGE_CACHE_DIR = "feed_image_cache"

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    /**
     * A feed-only [ImageLoader], isolated from `coil3.SingletonImageLoader` (the one every other
     * `AsyncImage` in the app implicitly uses): its own disk cache directory means it never
     * shares eviction pressure with, or otherwise affects, image caching for profile, leaderboard,
     * activity, settings, or upload.
     *
     * NOTE on cache freshness: the plan called for a `respectCacheHeaders(false)` toggle so a
     * cached image keeps rendering offline regardless of the CDN's Cache-Control/Expires headers.
     * That method doesn't exist in Coil 3.4.0 — `ImageLoader.Builder`/`DiskCache.Builder` have no
     * such option. It also isn't needed: Coil 3's default `CacheStrategy.read()` (see
     * `coil3.network.internal.DefaultCacheStrategy`, verified against the 3.4.0 sources) always
     * returns whatever is already in the disk cache without validating it against those headers —
     * a network re-fetch only happens on a cache miss. So an isolated disk/memory cache pair,
     * without any extra freshness flag, already gets the required behavior.
     */
    @Provides
    @Singleton
    @Named("feedImageLoader")
    fun provideFeedImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(FEED_IMAGE_CACHE_DIR))
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context)
                    .build()
            }
            .build()
    }
}
