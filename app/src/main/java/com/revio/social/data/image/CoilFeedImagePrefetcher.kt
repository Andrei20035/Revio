package com.revio.social.data.image

import android.content.Context
import coil3.ImageLoader
import coil3.network.HttpException
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * [FeedImagePrefetcher] over the feed's isolated `@Named("feedImageLoader")` [ImageLoader] (see
 * `di/ImageModule.kt`), so a prefetch and the `AsyncImage` that later reads the same URL hit the
 * exact same disk cache.
 */
@Singleton
class CoilFeedImagePrefetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("feedImageLoader") private val imageLoader: ImageLoader,
) : FeedImagePrefetcher {

    override suspend fun isCached(url: String): Boolean {
        val diskCache = imageLoader.diskCache ?: return false
        return diskCache.openSnapshot(url)?.use { true } ?: false
    }

    override suspend fun fetch(url: String): PrefetchOutcome {
        val request = ImageRequest.Builder(context)
            .data(url)
            .diskCacheKey(url)
            // A prefetch's only job is to populate the disk cache; skipping the memory cache
            // avoids holding a decoded bitmap for a card that may not be composed for a while.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()

        return when (val result = imageLoader.execute(request)) {
            is SuccessResult -> PrefetchOutcome.Success
            is ErrorResult -> classifyPrefetchFailure(result.throwable)
        }
    }
}

/**
 * Classifies a failed fetch as permanent (won't succeed on retry within this session) or
 * transient (worth retrying). `internal` so [FeedImagePrefetcher] callers never need to reason
 * about raw exceptions, but the mapping itself is still directly unit-testable.
 */
internal fun classifyPrefetchFailure(throwable: Throwable): PrefetchOutcome = when (throwable) {
    is HttpException -> {
        val code = throwable.response.code
        when (code) {
            404, 403 -> PrefetchOutcome.PermanentFailure("HTTP $code")
            429 -> PrefetchOutcome.TransientFailure("HTTP $code")
            in 500..599 -> PrefetchOutcome.TransientFailure("HTTP $code")
            // Other 4xx (400, 401, 410, ...) won't fix themselves on a plain retry.
            else -> PrefetchOutcome.PermanentFailure("HTTP $code")
        }
    }
    is UnknownHostException -> PrefetchOutcome.TransientFailure("DNS: ${throwable.message}")
    is SocketTimeoutException -> PrefetchOutcome.TransientFailure("Timeout: ${throwable.message}")
    is IOException -> PrefetchOutcome.TransientFailure("IO: ${throwable.message}")
    // Anything else (decode failure, malformed image, OOM, ...) is treated as permanent — a
    // second attempt at the same bytes is very unlikely to decode differently.
    else -> PrefetchOutcome.PermanentFailure(throwable.message ?: throwable::class.simpleName.orEmpty())
}
