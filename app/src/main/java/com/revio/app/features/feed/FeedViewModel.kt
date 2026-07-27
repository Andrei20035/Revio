package com.revio.app.features.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.NetworkConnectivityManager
import com.revio.app.core.network.isNetworkError
import com.revio.app.core.network.onValidatedReconnect
import com.revio.app.data.local.cache.FeedCache
import com.revio.app.data.local.preferences.UserPreferences
import com.revio.app.data.model.ReportReason
import com.revio.app.data.repository.CommentRepository
import com.revio.app.data.repository.LikeRepository
import com.revio.app.data.repository.PostRepository
import com.revio.app.data.repository.ReportRepository
import com.revio.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val reportRepository: ReportRepository,
    private val likeRepository: LikeRepository,
    private val commentRepository: CommentRepository,
    private val feedCache: FeedCache,
    private val connectivity: NetworkConnectivityManager,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var feedLoadJob: Job? = null
    private var ownerUserId: UUID? = null
    private var firstVisibleItemIndex: Int = 0

    init {
        loadCurrentUser()
        viewModelScope.launch { hydrateFromCache() }
        viewModelScope.launch {
            userRepository.currentUser.filterNotNull().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
        viewModelScope.launch {
            feedCache.observePosts().collect { posts ->
                _uiState.update { it.copy(feedPosts = posts) }
            }
        }
        viewModelScope.launch {
            connectivity.isInternetValidated.collect { validated ->
                _uiState.update { it.copy(isOffline = !validated) }
            }
        }
        viewModelScope.launch {
            connectivity.onValidatedReconnect().collect { onReconnected() }
        }
    }

    /**
     * Hydrates from the persistent cache before deciding whether to hit the network: an owner
     * mismatch (different logged-in user than the one the cache was written for) wipes it, a
     * stale-but-matching cache is trimmed and its pagination metadata restored. An empty cache
     * falls through to a normal first-page load (which itself short-circuits to Offline when
     * there's no connectivity); a non-empty cache renders immediately and, at most, queues a
     * silent freshness sync.
     */
    private suspend fun hydrateFromCache() {
        try {
            ownerUserId = resolveOwnerUserId()

            val meta = feedCache.readMeta()
            val cachedPostsAtHydration = feedCache.observePosts().first()
            // A cache persisted without an owner (see resolveOwnerUserId()) can't be attributed to
            // anyone, so a non-empty cache with a null owner is treated as a mismatch too — it must
            // not be shown to whichever user happens to log in next.
            val ownerMismatch = meta != null &&
                cachedPostsAtHydration.isNotEmpty() &&
                meta.ownerUserId != ownerUserId
            if (ownerMismatch) {
                feedCache.clear()
            } else {
                feedCache.trimTo(MAX_CACHED_POSTS)
            }

            val effectiveMeta = if (ownerMismatch) null else feedCache.readMeta()
            val cachedPosts = feedCache.observePosts().first()

            _uiState.update {
                it.copy(
                    feedPosts = cachedPosts,
                    phase = if (cachedPosts.isEmpty()) FeedPhase.LoadingFirstPage else FeedPhase.ShowingPosts,
                    nextCursor = effectiveMeta?.nextCursor,
                    hasMore = effectiveMeta?.hasMore ?: true,
                )
            }

            if (cachedPosts.isEmpty()) {
                loadFirstPage()
            } else {
                maybeSyncSilently()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A corrupt/failing cache (e.g. the first-ever Room DB creation, or a malformed
            // persisted value) must not leave the UI stuck in HydratingCache forever — fall back
            // to a normal first-page load against an assumed-empty cache.
            runCatching { feedCache.clear() }
            _uiState.update { it.copy(phase = FeedPhase.LoadingFirstPage) }
            loadFirstPage()
        }
    }

    /**
     * Resolves the logged-in user's id with a short wait for it to become non-null. A cache
     * written with a `null` owner can never be recognized as belonging to a *different* future
     * user, so it's worth a brief wait here rather than persisting an unattributable cache.
     */
    private suspend fun resolveOwnerUserId(): UUID? =
        withTimeoutOrNull(OWNER_ID_WAIT.toMillis()) { userPreferences.userId.filterNotNull().firstOrNull() }

    /**
     * Reacts to a validated reconnect. Auto-retry is only ever mandatory for the empty
     * no-internet state; a stuck load-more retries because the user is already waiting at the
     * bottom of the list; anything else is a best-effort, non-disruptive freshness sync.
     */
    private suspend fun onReconnected() {
        val state = _uiState.value
        when {
            state.content is FeedContent.NoInternet -> loadFirstPage()

            state.loadMoreError != null && state.hasMore && state.feedPosts.isNotEmpty() -> {
                _uiState.update { it.copy(loadMoreError = null) }
                loadNextPage()
            }

            else -> maybeSyncSilently()
        }
    }

    /**
     * Silently refreshes the first page when the cache is stale (and the list is scrolled near
     * the top, so the delete-and-replace refresh is imperceptible). Never touches loading flags
     * other than [FeedUiState.isSyncing], and is completely silent on failure — the user never
     * asked for this refresh, so there's nothing to report back.
     */
    private suspend fun maybeSyncSilently() {
        val state = _uiState.value
        if (feedLoadJob?.isActive == true) return
        if (state.feedPosts.isEmpty()) return
        if (state.phase is FeedPhase.FirstPageFailed || state.loadMoreError != null) return
        if (!connectivity.isNetworkAvailable.value) return
        if (firstVisibleItemIndex > PAGE_SIZE) return

        val meta = feedCache.readMeta()
        val isStale = meta?.lastSyncedAt?.let { Duration.between(it, Instant.now()) > STALE_AFTER } ?: true
        if (isStale) {
            load(reset = true, isRefresh = false, isSilent = true)
        }
    }

    /** Called from the feed list's scroll state; gates the silent sync so it never yanks content out from under the user. */
    fun onScrollPositionChanged(index: Int) {
        firstVisibleItemIndex = index
    }

    /** Initial load into an empty feed. */
    private fun loadFirstPage() = load(reset = true, isRefresh = false)

    /** Pull-to-refresh: reload from the top, keeping current content visible until it returns. */
    fun refresh() = load(reset = true, isRefresh = true)

    /** Infinite scroll: append the next page if there is one and nothing is already in flight. */
    fun loadNextPage() {
        val state = _uiState.value
        if (state.phase !is FeedPhase.ShowingPosts) return
        if (!state.hasMore || state.isAnyLoading) return
        load(reset = false, isRefresh = false)
    }

    /** Footer Retry tap — retries the next page only; offline, it never reaches the network. */
    fun onFooterRetry() = loadNextPage()

    /** Retry from the full-screen error state (the [LoadError.Generic] case — NoInternet auto-retries on its own). */
    fun onInitialRetry() = load(reset = true, isRefresh = false)

    private fun load(reset: Boolean, isRefresh: Boolean, isSilent: Boolean = false) {
        val isInitial = reset && !isRefresh && !isSilent
        val previousJob = feedLoadJob
        if (previousJob?.isActive == true && !isInitial) return

        _uiState.update { state ->
            val base = state.copy(
                isRefreshing = isRefresh,
                isLoadingMore = !reset,
                isSyncing = isSilent,
                phase = if (isInitial && state.phase !is FeedPhase.ShowingPosts) {
                    FeedPhase.LoadingFirstPage
                } else {
                    state.phase
                },
            )
            when {
                isSilent -> base
                isRefresh -> base.copy(refreshError = null)
                !reset -> base.copy(loadMoreError = null)
                else -> base.copy(initialLoadError = null)
            }
        }

        if (!connectivity.isNetworkAvailable.value) {
            _uiState.update { state ->
                val cleared = state.copy(
                    isRefreshing = false,
                    isLoadingMore = false,
                    isSyncing = false,
                    phase = if (isInitial) FeedPhase.FirstPageFailed(LoadError.Offline) else state.phase,
                )
                when {
                    isSilent -> cleared
                    isRefresh -> cleared.copy(
                        refreshError = LoadError.Offline,
                        userMessage = refreshErrorMessage(LoadError.Offline),
                    )
                    !reset -> cleared.copy(loadMoreError = LoadError.Offline)
                    else -> cleared.copy(initialLoadError = LoadError.Offline)
                }
            }
            return
        }

        feedLoadJob = viewModelScope.launch {
            if (isInitial) previousJob?.cancelAndJoin()

            val cursor = if (reset) null else _uiState.value.nextCursor

            when (val result = postRepository.getFeedPosts(limit = PAGE_SIZE, cursor = cursor)) {
                is ApiResult.Success -> {
                    val syncedAt = Instant.now()
                    val silentEmptySync = isSilent && reset && result.data.posts.isEmpty()
                    if (reset && !silentEmptySync) {
                        if (ownerUserId == null) {
                            ownerUserId = resolveOwnerUserId()
                        }
                        // A cache persisted with a null owner is recognized as unattributable and
                        // wiped as a mismatch the next time hydrateFromCache() resolves a real
                        // owner id (see the ownerMismatch check there), so it's still safe to
                        // persist this page even when the owner id hasn't resolved yet — the
                        // alternative (skipping the write) leaves the UI showing skeletons forever.
                        feedCache.replaceWithFirstPage(
                            page = result.data,
                            ownerUserId = ownerUserId,
                            syncedAt = syncedAt,
                        )
                    } else if (!reset) {
                        feedCache.appendPage(page = result.data, syncedAt = syncedAt)
                    } else if (silentEmptySync) {
                        // A silent sync (not user-initiated) that came back empty must not wipe a
                        // cached feed, but freshness still needs to advance — otherwise
                        // maybeSyncSilently() sees the same stale timestamp and re-fires this
                        // exact empty sync on every reconnect, forever.
                        feedCache.markSynced(syncedAt)
                    }
                    _uiState.update { state ->
                        val cleared = state.copy(
                            nextCursor = result.data.nextCursor,
                            hasMore = result.data.hasMore,
                            isRefreshing = false,
                            isLoadingMore = false,
                            isSyncing = false,
                            phase = when {
                                !reset -> state.phase
                                result.data.posts.isNotEmpty() -> FeedPhase.ShowingPosts
                                isSilent -> state.phase
                                else -> FeedPhase.ConfirmedEmpty
                            },
                        )
                        when {
                            isSilent -> cleared
                            isRefresh -> cleared.copy(refreshError = null)
                            !reset -> cleared.copy(loadMoreError = null)
                            else -> cleared.copy(initialLoadError = null)
                        }
                    }
                }

                is ApiResult.Error -> {
                    val error = if (result.isNetworkError) LoadError.Offline else LoadError.Generic(result.message)
                    _uiState.update { state ->
                        val cleared = state.copy(
                            isRefreshing = false,
                            isLoadingMore = false,
                            isSyncing = false,
                            phase = if (isInitial && state.feedPosts.isEmpty()) {
                                FeedPhase.FirstPageFailed(error)
                            } else {
                                state.phase
                            },
                        )
                        when {
                            isSilent -> cleared
                            isRefresh -> cleared.copy(
                                refreshError = error,
                                userMessage = refreshErrorMessage(error),
                            )
                            !reset -> cleared.copy(loadMoreError = error)
                            else -> cleared.copy(initialLoadError = error)
                        }
                    }
                }
            }
        }
    }

    /**
     * Guards a network-bound action: if there's no connectivity, surfaces [message] as a
     * snackbar and never runs [block] — no API call, no queued retry. Actions that need to
     * additionally unwind their own UI state (e.g. closing a dialog) when offline don't fit
     * this shape and guard inline instead.
     */
    private inline fun requireOnline(message: String, block: () -> Unit) {
        if (!connectivity.isNetworkAvailable.value) {
            _uiState.update { it.copy(userMessage = message) }
            return
        }
        block()
    }

    // ---- Post options / reporting ----

    /** A report reason was chosen from a post's options menu — open the confirmation dialog. */
    fun onReportReasonSelected(postId: UUID, reason: ReportReason) {
        _uiState.update { it.copy(reportDialog = ReportDialogState(postId, reason)) }
    }

    /** Dismiss the confirmation dialog without submitting (Cancel / tap outside). */
    fun dismissReportDialog() {
        _uiState.update { it.copy(reportDialog = null) }
    }

    /** Submit the report described by the open dialog. */
    fun confirmReport() {
        val dialog = _uiState.value.reportDialog ?: return
        if (dialog.isSubmitting) return

        if (!connectivity.isNetworkAvailable.value) {
            _uiState.update {
                it.copy(reportDialog = null, userMessage = "You're offline — your report wasn't sent.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(reportDialog = dialog.copy(isSubmitting = true)) }

            val message = when (reportRepository.reportPost(dialog.postId, dialog.reason)) {
                is ApiResult.Success -> "Report submitted. Thanks for helping keep Revio accurate."
                is ApiResult.Error -> "Couldn't submit your report. Please try again."
            }
            _uiState.update { it.copy(reportDialog = null, userMessage = message) }
        }
    }

    /** Acknowledge a one-shot snackbar message so it isn't shown again on recomposition. */
    fun consumeUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /** Clears the typed refresh-error slot once its snackbar message has been shown. */
    fun consumeRefreshError() {
        _uiState.update { it.copy(refreshError = null) }
    }

    /** Snackbar copy for a failed pull-to-refresh; the full-screen states never use this. */
    private fun refreshErrorMessage(error: LoadError): String = when (error) {
        LoadError.Offline -> "You're offline — couldn't refresh your feed."
        is LoadError.Generic -> "Couldn't refresh your feed. Pull to try again."
    }

    // ---- Likes ----

    /**
     * Toggle the like on [postId]. Updates the UI optimistically, then reconciles with the
     * server's authoritative count/state. On failure the optimistic change is reverted and a
     * message is surfaced. Taps while a toggle is already in flight for the post are ignored,
     * which prevents double-tap double-counting and duplicate backend calls.
     */
    fun onLikeToggle(postId: UUID) {
        val current = _uiState.value.feedPosts.firstOrNull { it.id == postId } ?: return
        if (postId in _uiState.value.likeInFlight) return

        requireOnline("You're offline — likes will work once you're back online.") {
            val wasLiked = current.likedByCurrentUser
            val optimisticCount = (current.likeCount + if (wasLiked) -1 else 1).coerceAtLeast(0)

            // Mark in-flight; the optimistic flip + count nudge below goes through the cache,
            // whose Flow reflects it into feedPosts immediately.
            _uiState.update { it.copy(likeInFlight = it.likeInFlight + postId) }

            viewModelScope.launch {
                feedCache.updateLike(postId, liked = !wasLiked, likeCount = optimisticCount)

                when (val result = likeRepository.toggleLike(postId)) {
                    is ApiResult.Success -> {
                        // Reconcile with the server's authoritative state.
                        feedCache.updateLike(postId, liked = result.data.liked, likeCount = result.data.count)
                        _uiState.update { it.copy(likeInFlight = it.likeInFlight - postId) }
                    }

                    is ApiResult.Error -> {
                        // Revert the optimistic change.
                        feedCache.updateLike(postId, liked = wasLiked, likeCount = current.likeCount)
                        _uiState.update {
                            it.copy(
                                likeInFlight = it.likeInFlight - postId,
                                userMessage = "Couldn't update your like. Please try again.",
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- Comments ----

    /** Open the comments overlay for [postId] and load its comments. */
    fun openComments(postId: UUID) {
        _uiState.update { it.copy(commentsSheet = CommentsSheetState(postId = postId, isLoading = true)) }
        loadComments(postId)
    }

    /** Reload comments for the currently open sheet (e.g. after an error). */
    fun retryLoadComments() {
        val sheet = _uiState.value.commentsSheet ?: return
        _uiState.update { it.copy(commentsSheet = sheet.copy(isLoading = true, errorMessage = null)) }
        loadComments(sheet.postId)
    }

    private fun loadComments(postId: UUID) {
        viewModelScope.launch {
            val result = commentRepository.getCommentsForPost(postId)
            // Keep the feed's comment count consistent with the authoritative server total.
            if (result is ApiResult.Success) {
                feedCache.setCommentCount(postId, result.data.size.toLong())
            }
            _uiState.update { state ->
                // Ignore if the sheet was closed or switched to another post meanwhile.
                val sheet = state.commentsSheet?.takeIf { it.postId == postId } ?: return@update state
                state.copy(
                    commentsSheet = when (result) {
                        is ApiResult.Success -> sheet.copy(comments = result.data, isLoading = false, errorMessage = null)
                        is ApiResult.Error -> sheet.copy(
                            isLoading = false,
                            errorMessage = if (result.isNetworkError) {
                                "You're offline — comments couldn't be loaded."
                            } else {
                                result.message
                            },
                        )
                    }
                )
            }
        }
    }

    fun closeComments() {
        _uiState.update { it.copy(commentsSheet = null) }
    }

    fun onCommentDraftChange(text: String) {
        _uiState.update { state ->
            val sheet = state.commentsSheet ?: return@update state
            state.copy(commentsSheet = sheet.copy(draft = text))
        }
    }

    /** Publish the draft comment to the open sheet's post; updates the list and the feed count. */
    fun submitComment() {
        val sheet = _uiState.value.commentsSheet ?: return
        val text = sheet.draft.trim()
        if (text.isEmpty() || sheet.isSubmitting) return

        requireOnline("You're offline — your comment wasn't posted.") {
            viewModelScope.launch {
                _uiState.update { state ->
                    val s = state.commentsSheet ?: return@update state
                    state.copy(commentsSheet = s.copy(isSubmitting = true))
                }

                when (val result = commentRepository.addComment(sheet.postId, text)) {
                    is ApiResult.Success -> {
                        // Keep the feed's comment count consistent with the new comment.
                        val currentCount = _uiState.value.feedPosts.firstOrNull { it.id == sheet.postId }?.commentCount ?: 0
                        feedCache.setCommentCount(sheet.postId, currentCount + 1)

                        _uiState.update { state ->
                            val s = state.commentsSheet?.takeIf { it.postId == sheet.postId }
                            state.copy(
                                // Append the new comment (server orders oldest-first) and clear the input.
                                commentsSheet = s?.copy(
                                    comments = s.comments + result.data,
                                    draft = "",
                                    isSubmitting = false,
                                ) ?: state.commentsSheet,
                            )
                        }
                    }

                    is ApiResult.Error -> _uiState.update { state ->
                        val s = state.commentsSheet ?: return@update state
                        state.copy(
                            commentsSheet = s.copy(isSubmitting = false),
                            userMessage = "Couldn't post your comment. Please try again.",
                        )
                    }
                }
            }
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            when (val result = userRepository.getCurrentUser()) {
                is ApiResult.Success -> _uiState.update { it.copy(currentUser = result.data) }
                is ApiResult.Error -> Unit // header avatar falls back to placeholder; not fatal to the feed
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 15
        private const val MAX_CACHED_POSTS = 90
        private val STALE_AFTER: Duration = Duration.ofMinutes(30)
        private val OWNER_ID_WAIT: Duration = Duration.ofSeconds(2)
    }
}
