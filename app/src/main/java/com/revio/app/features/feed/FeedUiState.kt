package com.revio.app.features.feed

import com.revio.app.data.model.Comment
import com.revio.app.data.model.FeedPost
import com.revio.app.data.model.ReportReason
import com.revio.app.data.model.User
import com.revio.app.data.remote.dto.post.FeedCursor
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
    val nextCursor: FeedCursor? = null,
    val hasMore: Boolean = true,

    // The cache read has completed (empty or not) — gates the skeleton so it can't outlive hydration.
    val isCacheHydrated: Boolean = false,
    // Mirrors NetworkConnectivityManager.isInternetValidated, inverted.
    val isOffline: Boolean = false,

    // First page into an empty list — show a full-screen loader.
    val isLoadingInitial: Boolean = false,
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
        get() = feedPosts.isEmpty()

    val isAnyLoading: Boolean
        get() = isLoadingInitial || isRefreshing || isLoadingMore || isSyncing

    /** What the main content area should render. */
    val content: FeedContent
        get() = when {
            !isCacheHydrated || isLoadingInitial -> FeedContent.Skeletons
            feedPosts.isNotEmpty() -> FeedContent.Posts
            initialLoadError is LoadError.Offline -> FeedContent.NoInternet
            initialLoadError is LoadError.Generic -> FeedContent.Error(initialLoadError.message)
            else -> FeedContent.Empty
        }

    /** What the list's trailing footer item should render. */
    val footer: FeedFooterState
        get() = when {
            feedPosts.isEmpty() -> FeedFooterState.Hidden
            isLoadingMore -> FeedFooterState.Loading
            loadMoreError is LoadError.Offline -> FeedFooterState.OfflineRetry
            loadMoreError is LoadError.Generic -> FeedFooterState.ErrorRetry(loadMoreError.message)
            !hasMore -> FeedFooterState.CaughtUp
            else -> FeedFooterState.Idle
        }
}
