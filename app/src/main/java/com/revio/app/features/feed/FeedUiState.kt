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

data class FeedUiState(
    val currentUser: User? = null,

    val feedPosts: List<FeedPost> = emptyList(),
    val nextCursor: FeedCursor? = null,
    val hasMore: Boolean = true,

    // First page into an empty list — show a full-screen loader.
    val isLoadingInitial: Boolean = false,
    // Pull-to-refresh while content may already be on screen.
    val isRefreshing: Boolean = false,
    // Appending the next page — show a footer loader.
    val isLoadingMore: Boolean = false,

    val errorMessage: String? = null,

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
        get() = isLoadingInitial || isRefreshing || isLoadingMore
}
