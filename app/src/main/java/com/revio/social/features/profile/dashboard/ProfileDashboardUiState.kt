package com.revio.social.features.profile.dashboard

import com.revio.social.data.model.FeedPost
import com.revio.social.data.model.User
import com.revio.social.data.remote.dto.post.FeedCursor
import com.revio.social.features.feed.CommentsSheetState
import java.util.UUID

data class ProfileDashboardUiState(
    val isOwnProfile: Boolean = true,
    val currentUserId: UUID? = null,
    /** Whether the logged-in viewer (not necessarily [user], the profile being displayed) is an admin. */
    val isCurrentUserAdmin: Boolean = false,
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
    /** Images that failed to load (per post + imageUrl), pending retry or placeholder display. */
    val failedImages: Set<PostImageKey> = emptySet(),
    /** Retry generation per image — bumping the token forces the AsyncImage request to restart. */
    val imageRetryTokens: Map<PostImageKey, Int> = emptyMap(),
    /** Images already covered by the one-shot auto-retry on screen entry. */
    val autoRetriedImages: Set<PostImageKey> = emptySet(),
    /** Posts with an in-flight post-detail refresh (triggered by opening the see-post overlay). */
    val postDetailInFlight: Set<UUID> = emptySet(),
    /** Wall-clock time (ms) of the last successful post-detail refresh per post, for TTL-based dedup. */
    val postDetailFetchedAt: Map<UUID, Long> = emptyMap(),
    /**
     * A post fetched via [ProfileDashboardViewModel.openPostFromDeepLink] because [selectedPostId]
     * wasn't in [posts] (e.g. a push deep link to a spot outside the loaded page, D3). Kept
     * separate from [posts] rather than inserted into it, so it never disturbs grid order or
     * cursor pagination.
     */
    val deepLinkedPost: FeedPost? = null,
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
        get() = selectedPostId?.let { id ->
            posts.firstOrNull { it.id == id } ?: deepLinkedPost?.takeIf { it.id == id }
        }

}
