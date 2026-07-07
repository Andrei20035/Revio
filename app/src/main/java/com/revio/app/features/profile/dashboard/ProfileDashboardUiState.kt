package com.revio.app.features.profile.dashboard

import com.revio.app.data.model.FeedPost
import com.revio.app.data.model.User
import com.revio.app.data.remote.dto.post.FeedCursor
import com.revio.app.features.feed.CommentsSheetState
import java.util.UUID

data class ProfileDashboardUiState(
    val isOwnProfile: Boolean = true,
    val currentUserId: UUID? = null,
    val user: User? = null,
    val posts: List<FeedPost> = emptyList(),
    val nextCursor: FeedCursor? = null,
    val hasMore: Boolean = true,
    val isLoadingUser: Boolean = false,
    val isLoadingInitial: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    /** Non-null while the see-post overlay is open. */
    val selectedPostId: UUID? = null,
    /** Non-null while the comments sheet is open (scoped to the selected post). */
    val commentsSheet: CommentsSheetState? = null,
    /** Posts with an in-flight like toggle — prevents double taps. */
    val likeInFlight: Set<UUID> = emptySet(),
    /** One-shot error message (e.g. like failure); cleared after shown. */
    val userMessage: String? = null,
    /** True while the delete-confirmation dialog is open for the selected post. */
    val showDeleteConfirm: Boolean = false,
    /** True while the Early Spotter info overlay is open. */
    val showEarlySpotterInfo: Boolean = false,
    /** Post id whose delete request is in flight — guards against double-tap. */
    val deleteInFlight: UUID? = null,
) {
    val isAnyLoading: Boolean
        get() = isLoadingInitial || isLoadingMore || isRefreshing

    val isEmpty: Boolean
        get() = posts.isEmpty()

    val postCount: Int
        get() = user?.postCount ?: 0

    val streakDays: Int
        get() = user?.streakDays ?: 0

    val selectedPost: FeedPost?
        get() = selectedPostId?.let { id -> posts.firstOrNull { it.id == id } }

}
