package com.revio.social.features.feed

import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.data.image.FeedImagePrefetcher
import com.revio.social.data.image.PrefetchOutcome
import com.revio.social.data.model.FeedPost
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Per-image gating state — see the feed image-loading plan's §7 state machine. */
sealed interface ImageState {
    data object Pending : ImageState
    data object Queued : ImageState
    data object Fetching : ImageState
    data object Ready : ImageState
    data class Skipped(val permanent: Boolean) : ImageState
}

/** Gate-wide status, orthogonal to any single image's state — see [FeedImageGate.status]. */
sealed interface GateStatus {
    /** Nothing in flight, no shortfall — steady state. */
    data object Idle : GateStatus

    /** At least one fetch is currently in flight. */
    data object Working : GateStatus

    /**
     * Every known candidate is terminal, at least one never became `Ready`, and no more pages are
     * obtainable (no refill budget left, or the caller reports `hasMore = false`). Covers a page
     * whose images are all unusable (e.g. a bad storage base URL) surviving every retry and
     * refill attempt.
     */
    data object ExhaustedNoContent : GateStatus
}

/**
 * ev. 23 — a permanent or transient image-fetch skip, with [classifyPrefetchFailure]'s fixed
 * `reason` code now actually reaching somewhere instead of being discarded (pas 2.6c).
 */
private const val EVENT_FEED_IMAGE_GATE = "feed_image_gate"

private const val MAX_CONCURRENT_FETCHES = 3
private const val READY_BUFFER_TARGET = 3
private const val TRANSIENT_RETRY_DELAY_MS = 300L
private const val IMAGE_FETCH_TIMEOUT_MS = 10_000L
private const val FIRST_PAINT_DEADLINE_MS = 5_000L
private const val MAX_REFILL_PAGES = 2

/**
 * Gates feed posts behind their main image's availability: a post only reaches [visiblePosts]
 * once its image is verified cached or freshly prefetched. Publication preserves the original
 * candidate order — a post whose fetch reaches a terminal failure is skipped, never blocking the
 * posts behind it — and is strictly append-only within a session (a `resetToken` bump in
 * [onCandidates] is the only thing that clears it). No Android dependency, so this is directly
 * unit-testable on the JVM against a fake [FeedImagePrefetcher].
 *
 * Covers the feed image-loading plan's §8.1-§8.7: ordering/skip/concurrency/buffer (§8.1-§8.4),
 * a single retry for transient failures under a per-attempt timeout, a first-paint deadline that
 * demotes a stuck head only while [visiblePosts] is still empty (§8.5), reconnect requeueing of
 * retryable skips that haven't been passed by the frontier yet (§8.7), and a bounded page-refill
 * signal (§8.4). [onRefillNeeded] is invoked to ask the caller for another page — this class has
 * no knowledge of pagination cursors or network calls, so wiring that callback to an actual
 * `loadNextPage()` is left to the caller.
 */
class FeedImageGate(
    private val prefetcher: FeedImagePrefetcher,
    private val scope: CoroutineScope,
    private val onRefillNeeded: () -> Unit = {},
    private val isNetworkAvailable: () -> Boolean = { true },
    private val analyticsClient: AnalyticsClient? = null,
) {
    private val mutex = Mutex()
    private val semaphore = Semaphore(MAX_CONCURRENT_FETCHES)

    private var candidates: List<FeedPost> = emptyList()
    private val states = mutableMapOf<UUID, ImageState>()
    private val inFlight = mutableMapOf<UUID, Job>()
    private var publishFrontier = 0
    private var lastResetToken: Int? = null
    private var hasMore: Boolean = true

    private var firstPaintWatcher: Job? = null
    private var firstPaintWatcherPostId: UUID? = null

    private var refillPagesRequested = 0
    private var refillRequestPending = false

    private val _visiblePosts = MutableStateFlow<List<FeedPost>>(emptyList())
    val visiblePosts: StateFlow<List<FeedPost>> = _visiblePosts.asStateFlow()

    private val _status = MutableStateFlow<GateStatus>(GateStatus.Idle)
    val status: StateFlow<GateStatus> = _status.asStateFlow()

    /**
     * Applies the latest candidate list from the feed cache. A [resetToken] different from the
     * one seen on the previous call means a first-page replace happened (refresh / initial load /
     * owner mismatch) — all gate state and [visiblePosts] are wiped before the new list is
     * applied. Otherwise per-post state is preserved by id: already-tracked posts keep their
     * progress (an appended page doesn't restart fetches already in flight or already resolved).
     * [hasMore] mirrors the feed cache's pagination metadata and gates the refill signal.
     */
    fun onCandidates(newCandidates: List<FeedPost>, resetToken: Int, hasMore: Boolean = true) {
        scope.launch {
            mutex.withLock {
                syncCandidates(newCandidates, resetToken, hasMore)
                pumpFetches()
                advanceFrontier()
            }
        }
    }

    /**
     * Reacts to a validated network reconnect: resets the refill budget and requeues every
     * `Skipped(permanent = false)` candidate that the publish frontier hasn't passed yet. A post
     * already left behind by the frontier stays skipped for this session — reviving it would mean
     * inserting it above content the user has already scrolled past.
     */
    fun onReconnect() {
        scope.launch {
            mutex.withLock {
                refillPagesRequested = 0
                refillRequestPending = false
                candidates.forEachIndexed { index, post ->
                    if (index < publishFrontier) return@forEachIndexed
                    val state = states[post.id]
                    if (state is ImageState.Skipped && !state.permanent) {
                        states[post.id] = ImageState.Pending
                    }
                }
                pumpFetches()
                advanceFrontier()
            }
        }
    }

    private fun syncCandidates(newCandidates: List<FeedPost>, resetToken: Int, hasMore: Boolean) {
        if (lastResetToken == null || resetToken != lastResetToken) {
            lastResetToken = resetToken
            states.clear()
            inFlight.values.forEach { it.cancel() }
            inFlight.clear()
            publishFrontier = 0
            _visiblePosts.value = emptyList()
            firstPaintWatcher?.cancel()
            firstPaintWatcher = null
            firstPaintWatcherPostId = null
            refillPagesRequested = 0
            refillRequestPending = false
        } else {
            val newIds = newCandidates.mapTo(mutableSetOf()) { it.id }
            states.keys.filterNot { it in newIds }.forEach { staleId ->
                states.remove(staleId)
                inFlight.remove(staleId)?.cancel()
            }
            // More candidates landed (a requested page arrived) — allow another refill request
            // if the buffer is still thin; the per-cycle cap is untouched by this.
            if (newCandidates.size > candidates.size) {
                refillRequestPending = false
            }
        }

        this.hasMore = hasMore
        candidates = newCandidates
        for (post in newCandidates) {
            states.putIfAbsent(post.id, ImageState.Pending)
        }
    }

    /**
     * Starts fetches for `Pending` candidates in order, bounded by [MAX_CONCURRENT_FETCHES]
     * in-flight jobs and by [READY_BUFFER_TARGET] already-`Ready` posts waiting beyond the
     * publish frontier — once that many are buffered, no new fetch is started even if a
     * concurrency slot is free.
     */
    private suspend fun pumpFetches() {
        if (countReadyAhead() >= READY_BUFFER_TARGET) return

        for (post in candidates) {
            if (states[post.id] != ImageState.Pending) continue
            if (inFlight.size >= MAX_CONCURRENT_FETCHES) break

            if (prefetcher.isCached(post.imageUrl)) {
                states[post.id] = ImageState.Ready
                continue
            }

            if (!isNetworkAvailable()) {
                states[post.id] = ImageState.Skipped(permanent = false)
                continue
            }

            states[post.id] = ImageState.Queued
            inFlight[post.id] = scope.launch {
                semaphore.withPermit {
                    mutex.withLock { states[post.id] = ImageState.Fetching }
                    val finalState = resolveFetch(post.imageUrl)
                    mutex.withLock {
                        states[post.id] = finalState
                        inFlight.remove(post.id)
                        advanceFrontier()
                        pumpFetches()
                    }
                }
            }
        }
    }

    /**
     * Runs one fetch attempt under [IMAGE_FETCH_TIMEOUT_MS]. A permanent failure (or success) is
     * final after a single attempt; a transient failure or a timeout gets exactly one retry after
     * [TRANSIENT_RETRY_DELAY_MS] before being given up on for this session.
     */
    private suspend fun resolveFetch(url: String): ImageState {
        val first = fetchWithTimeout(url)
        if (first is PrefetchOutcome.Success) return ImageState.Ready
        if (first is PrefetchOutcome.PermanentFailure) {
            logImageGateResult(permanent = true, reason = first.reason)
            return ImageState.Skipped(permanent = true)
        }

        delay(TRANSIENT_RETRY_DELAY_MS)
        val second = fetchWithTimeout(url)
        return when (second) {
            is PrefetchOutcome.Success -> ImageState.Ready
            is PrefetchOutcome.PermanentFailure -> {
                logImageGateResult(permanent = true, reason = second.reason)
                ImageState.Skipped(permanent = true)
            }
            is PrefetchOutcome.TransientFailure -> {
                logImageGateResult(permanent = false, reason = second.reason)
                ImageState.Skipped(permanent = false)
            }
            // Second attempt itself timed out — no PrefetchOutcome to read a reason from.
            null -> {
                logImageGateResult(permanent = false, reason = "timeout")
                ImageState.Skipped(permanent = false)
            }
        }
    }

    private fun logImageGateResult(permanent: Boolean, reason: String) {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_FEED_IMAGE_GATE,
                params = mapOf(
                    "outcome" to AnalyticsParamValue.StringValue(if (permanent) "permanent" else "transient"),
                    "reason" to AnalyticsParamValue.StringValue(reason),
                ),
            )
        )
    }

    private suspend fun fetchWithTimeout(url: String): PrefetchOutcome? =
        withTimeoutOrNull(IMAGE_FETCH_TIMEOUT_MS) { prefetcher.fetch(url) }

    /** Ready posts beyond [publishFrontier], regardless of what's interleaved between them. */
    private fun countReadyAhead(): Int =
        candidates.asSequence().drop(publishFrontier).count { states[it.id] == ImageState.Ready }

    /**
     * Walks the candidate prefix from [publishFrontier]: `Ready` is published, `Skipped` is
     * dropped without blocking anything behind it, and the first non-terminal state stops the
     * walk (head-of-line). Publication only ever appends to [visiblePosts].
     */
    private fun advanceFrontier() {
        var i = publishFrontier
        val newlyPublished = mutableListOf<FeedPost>()
        while (i < candidates.size) {
            when (states[candidates[i].id]) {
                ImageState.Ready -> {
                    newlyPublished += candidates[i]
                    i++
                }
                is ImageState.Skipped -> i++
                else -> break
            }
        }
        publishFrontier = i
        if (newlyPublished.isNotEmpty()) {
            _visiblePosts.value = _visiblePosts.value + newlyPublished
        }
        maybeScheduleFirstPaintWatcher()
        maybeRequestRefill()
        updateGateStatus()
    }

    /**
     * While [visiblePosts] is still empty, a stuck head blocks the very first card from ever
     * appearing. After [FIRST_PAINT_DEADLINE_MS] with no other content on screen, the head is
     * demoted to a retryable skip so the frontier can move — its fetch job keeps running and may
     * still populate the cache for a later reconnect/refresh. Once anything has published, this
     * deadline no longer applies; a stalled item deeper in the list is fine to wait on.
     */
    private fun maybeScheduleFirstPaintWatcher() {
        if (_visiblePosts.value.isNotEmpty() || publishFrontier >= candidates.size) {
            firstPaintWatcher?.cancel()
            firstPaintWatcher = null
            firstPaintWatcherPostId = null
            return
        }

        val head = candidates[publishFrontier]
        if (firstPaintWatcherPostId == head.id && firstPaintWatcher?.isActive == true) return

        firstPaintWatcher?.cancel()
        firstPaintWatcherPostId = head.id
        firstPaintWatcher = scope.launch {
            delay(FIRST_PAINT_DEADLINE_MS)
            mutex.withLock {
                val stillHead = publishFrontier < candidates.size && candidates[publishFrontier].id == head.id
                val stillNonTerminal = states[head.id].let { it != ImageState.Ready && it !is ImageState.Skipped }
                if (_visiblePosts.value.isEmpty() && stillHead && stillNonTerminal) {
                    states[head.id] = ImageState.Skipped(permanent = false)
                    advanceFrontier()
                    pumpFetches()
                }
            }
        }
    }

    /**
     * Signals [onRefillNeeded] when fewer than [READY_BUFFER_TARGET] candidates remain beyond the
     * frontier to draw fetches from — the pipeline is about to run dry regardless of how those
     * remaining candidates resolve. Capped at [MAX_REFILL_PAGES] requests per cycle (reset by a
     * full [onCandidates] reset or by [onReconnect]) and silenced again as soon as a request is
     * made, until a bigger candidate list (the requested page landing) reopens it.
     */
    private fun maybeRequestRefill() {
        if (refillRequestPending) return
        if (!hasMore) return
        if (!isNetworkAvailable()) return
        if (refillPagesRequested >= MAX_REFILL_PAGES) return
        if (candidates.size - publishFrontier >= READY_BUFFER_TARGET) return

        refillRequestPending = true
        refillPagesRequested++
        onRefillNeeded()
    }

    /**
     * Recomputes [status]. [GateStatus.ExhaustedNoContent] requires every known candidate to be
     * terminal (the frontier reached the end), a genuine shortfall (fewer posts published than
     * candidates known — i.e. something was actually skipped, not just "caught up"), and no more
     * pages obtainable. A fully successful, merely-caught-up feed never matches this.
     */
    private fun updateGateStatus() {
        val allResolved = candidates.isNotEmpty() && publishFrontier >= candidates.size
        val shortfall = _visiblePosts.value.size < candidates.size
        val outOfOptions = !hasMore ||
            !isNetworkAvailable() ||
            (refillPagesRequested >= MAX_REFILL_PAGES && !refillRequestPending)

        _status.value = when {
            allResolved && shortfall && outOfOptions -> GateStatus.ExhaustedNoContent
            inFlight.isNotEmpty() -> GateStatus.Working
            else -> GateStatus.Idle
        }
    }
}
