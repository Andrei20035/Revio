package com.revio.social.data.image

/**
 * Prefetch contract for feed images, decoupled from Coil so the feed's gating logic can be
 * unit-tested on the JVM without an Android [coil3.ImageLoader]. Implementations own no ordering,
 * concurrency, or retry logic — those live in the caller.
 */
interface FeedImagePrefetcher {

    /** True if [url]'s bytes are already on disk — checkable without touching the network. */
    suspend fun isCached(url: String): Boolean

    /** Fetches and decodes [url] through the feed's image loader, populating its disk cache entry. */
    suspend fun fetch(url: String): PrefetchOutcome
}

/** Outcome of a single [FeedImagePrefetcher.fetch] call, classified for retry/skip decisions. */
sealed interface PrefetchOutcome {
    /** The image was fetched (or was already cached) and decoded successfully. */
    data object Success : PrefetchOutcome

    /** Won't succeed on retry within this session (404/403, corrupt image, decode failure). */
    data class PermanentFailure(val reason: String) : PrefetchOutcome

    /** May succeed later (timeout, DNS, offline, 429/5xx) — worth a retry. */
    data class TransientFailure(val reason: String) : PrefetchOutcome
}
