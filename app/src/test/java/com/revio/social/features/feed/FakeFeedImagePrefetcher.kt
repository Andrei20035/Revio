package com.revio.social.features.feed

import com.revio.social.data.image.FeedImagePrefetcher
import com.revio.social.data.image.PrefetchOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * In-memory [FeedImagePrefetcher] double for JVM tests — no Coil, no Android [android.content.Context].
 * Mirrors [com.revio.social.data.image.CoilFeedImagePrefetcher]'s contract closely enough for
 * `FeedImageGate` tests: per-URL cached state and fetch outcome, an optional delay to exercise
 * timeouts, a manual hold/release gate per URL to deterministically freeze a [fetch] mid-flight,
 * and a live/observed count of concurrent [fetch] calls to assert concurrency limits.
 */
class FakeFeedImagePrefetcher(
    private val fetchDelayMs: Long = 0,
) : FeedImagePrefetcher {

    /** URLs considered already present on disk — [isCached] returns true without a [fetch] call. */
    val cachedUrls: MutableSet<String> = mutableSetOf()

    /** Outcome [fetch] returns for a given URL; defaults to [PrefetchOutcome.Success] if unset. */
    val outcomes: MutableMap<String, PrefetchOutcome> = mutableMapOf()

    /** Every URL passed to [fetch], in call order — lets tests assert ordering/skip behavior. */
    val fetchCalls: MutableList<String> = mutableListOf()

    /** Number of [fetch] calls currently in flight. */
    var activeFetchCount: Int = 0
        private set

    /** Highest [activeFetchCount] observed — lets tests assert a concurrency cap was respected. */
    var maxObservedConcurrency: Int = 0
        private set

    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** [fetch] for [url] suspends indefinitely until [release] is called for the same URL. */
    fun hold(url: String) {
        gates.getOrPut(url) { CompletableDeferred() }
    }

    /** Lets a held [fetch] for [url] proceed. No-op if [url] was never held. */
    fun release(url: String) {
        gates[url]?.complete(Unit)
    }

    override suspend fun isCached(url: String): Boolean = url in cachedUrls

    override suspend fun fetch(url: String): PrefetchOutcome {
        fetchCalls += url
        activeFetchCount++
        maxObservedConcurrency = maxOf(maxObservedConcurrency, activeFetchCount)
        try {
            gates[url]?.await()
            if (fetchDelayMs > 0) delay(fetchDelayMs)
            return outcomes[url] ?: PrefetchOutcome.Success
        } finally {
            activeFetchCount--
        }
    }
}
