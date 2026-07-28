package com.revio.social.features.feed

import com.revio.social.data.model.Comment
import com.revio.social.data.model.FeedPost
import com.revio.social.data.model.ReportReason
import com.revio.social.data.model.User
import com.revio.social.data.remote.dto.post.FeedCursor
import java.util.UUID

/** Confirmation dialog shown after a report reason is picked from the post-options menu. */
data class ReportDialogState(
    val postId: UUID,
    val reason: ReportReason,
    val isSubmitting: Boolean = false,
)

/** State for the Instagram-style comments overlay, scoped to the post it was opened for. */
data class CommentsSheetState(
    val postId: UUID,
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val draft: String = "",
    val isSubmitting: Boolean = false,
) {
    /** Send is allowed only with non-blank text and no submission already in flight. */
    val canSend: Boolean
        get() = draft.isNotBlank() && !isSubmitting
}

/** A load failure, distinguishing "no internet" from any other (server/parse/etc.) error. */
sealed interface LoadError {
    data object Offline : LoadError
    data class Generic(val message: String) : LoadError
}

/** What the feed's main content area should render. */
sealed interface FeedContent {
    data object Skeletons : FeedContent
    data object NoInternet : FeedContent
    data object Empty : FeedContent
    data class Error(val message: String) : FeedContent
    data object Posts : FeedContent
}

/**
 * The authoritative result of the first page — the single source of truth for
 * Skeletons/Empty/NoInternet/Error. Unlike the old boolean flags, [Empty] and the error variants
 * are only reachable through an explicit transition driven by a first-page result, never as a
 * derived fallback.
 */
sealed interface FeedPhase {
    /** The cache hasn't been read yet — we know nothing. */
    data object HydratingCache : FeedPhase

    /** Confirmed-empty cache, first page in flight (or cache hasn't propagated to the UI yet). */
    data object LoadingFirstPage : FeedPhase

    /** We authoritatively know there is at least one post, from cache or from the network. */
    data object ShowingPosts : FeedPhase

    /** The first page succeeded and returned zero posts. */
    data object ConfirmedEmpty : FeedPhase

    /** The first page failed and there's no content to show. */
    data class FirstPageFailed(val error: LoadError) : FeedPhase
}

/** What the list's trailing footer item should render. */
sealed interface FeedFooterState {
    data object Hidden : FeedFooterState
    data object Idle : FeedFooterState
    data object Loading : FeedFooterState
    data object OfflineRetry : FeedFooterState
    data class ErrorRetry(val message: String) : FeedFooterState
    data object CaughtUp : FeedFooterState
}

data class FeedUiState(
    val currentUser: User? = null,

    val feedPosts: List<FeedPost> = emptyList(),
    // Gated projection of feedPosts — a post appears here only once its image is prefetched or
    // verified cached. Rendered by FeedScreen; content/footer/isEmpty below key off this, not
    // feedPosts, so nothing shows until its image is actually ready.
    val visiblePosts: List<FeedPost> = emptyList(),
    val nextCursor: FeedCursor? = null,
    val hasMore: Boolean = true,

    // Mirrors NetworkConnectivityManager.isInternetValidated, inverted.
    val isOffline: Boolean = false,

    // Authoritative first-page result — single source of truth for Skeletons/Empty/NoInternet/Error.
    val phase: FeedPhase = FeedPhase.HydratingCache,

    // Pull-to-refresh while content may already be on screen.
    val isRefreshing: Boolean = false,
    // Appending the next page — show a footer loader.
    val isLoadingMore: Boolean = false,
    // Silent freshness refresh on reconnect with a stale cache — no spinner, no skeleton.
    val isSyncing: Boolean = false,

    // Initial-load failure into an empty feed — drives the full-screen NoInternet/Error content.
    val initialLoadError: LoadError? = null,
    // Load-more failure — drives the footer's OfflineRetry/ErrorRetry state.
    val loadMoreError: LoadError? = null,
    // Pull-to-refresh failure — surfaced as a transient snackbar only, never a full-screen state.
    val refreshError: LoadError? = null,

    // Non-null while the "Submit Report" confirmation dialog is open.
    val reportDialog: ReportDialogState? = null,
    // One-shot message surfaced in a snackbar (e.g. "Report submitted"); cleared after shown.
    val userMessage: String? = null,

    // Posts with an in-flight like toggle — used to ignore double taps and block duplicate calls.
    val likeInFlight: Set<UUID> = emptySet(),
    // Non-null while the comments overlay is open, scoped to a single post.
    val commentsSheet: CommentsSheetState? = null,
) {
    val isEmpty: Boolean
        get() = visiblePosts.isEmpty()

    val isAnyLoading: Boolean
        get() = phase is FeedPhase.LoadingFirstPage || isRefreshing || isLoadingMore || isSyncing

    /**
     * What the main content area should render, driven entirely by [phase]. [FeedContent.Empty]
     * is reachable only through [FeedPhase.ConfirmedEmpty] — an explicit transition set from a
     * successful first-page result — never as a derived fallback for "nothing else matched".
     */
    val content: FeedContent
        get() = when (val currentPhase = phase) {
            FeedPhase.HydratingCache, FeedPhase.LoadingFirstPage -> FeedContent.Skeletons
            // We authoritatively have posts, but their images haven't gated through to
            // visiblePosts yet — keep showing the shimmer instead of a transient Empty.
            FeedPhase.ShowingPosts ->
                if (visiblePosts.isEmpty()) FeedContent.Skeletons else FeedContent.Posts
            FeedPhase.ConfirmedEmpty -> FeedContent.Empty
            is FeedPhase.FirstPageFailed -> when {
                visiblePosts.isNotEmpty() -> FeedContent.Posts
                currentPhase.error is LoadError.Offline -> FeedContent.NoInternet
                else -> FeedContent.Error((currentPhase.error as LoadError.Generic).message)
            }
        }

    /** What the list's trailing footer item should render. */
    val footer: FeedFooterState
        get() = when {
            visiblePosts.isEmpty() -> FeedFooterState.Hidden
            isLoadingMore -> FeedFooterState.Loading
            loadMoreError is LoadError.Offline -> FeedFooterState.OfflineRetry
            loadMoreError is LoadError.Generic -> FeedFooterState.ErrorRetry(loadMoreError.message)
            !hasMore -> FeedFooterState.CaughtUp
            else -> FeedFooterState.Idle
        }
}
