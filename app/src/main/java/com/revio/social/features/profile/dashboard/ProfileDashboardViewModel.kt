package com.revio.social.features.profile.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.feedback.PostRemovalReason
import com.revio.social.core.feedback.PostRemovalSignal
import com.revio.social.core.feedback.PostRemovedEvent
import com.revio.social.core.navigation.Screen
import com.revio.social.core.network.ApiResult
import com.revio.social.core.notifications.PendingDeepLink
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.model.FeedPost
import com.revio.social.data.model.User
import com.revio.social.data.repository.CommentRepository
import com.revio.social.data.repository.LikeRepository
import com.revio.social.data.repository.PostRepository
import com.revio.social.data.repository.UserRepository
import com.revio.social.features.feed.CommentsSheetState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Ev. — pas 5.7: same events as FeedViewModel's like/comment results, fired from this screen too. */
private const val EVENT_FEED_LIKE_RESULT = "feed_like_result"
private const val EVENT_FEED_COMMENT_RESULT = "feed_comment_result"

@HiltViewModel
class ProfileDashboardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val likeRepository: LikeRepository,
    private val commentRepository: CommentRepository,
    private val userPreferences: UserPreferences,
    private val postRemovalSignal: PostRemovalSignal,
    private val analyticsClient: AnalyticsClient? = null,
    private val pendingDeepLink: PendingDeepLink? = null,
) : ViewModel() {

    /** Mirrors FeedViewModel.logInteractionResult's shape (pas 5.7). */
    private fun logInteractionResult(eventName: String, result: ApiResult<*>) {
        val params = buildMap<String, AnalyticsParamValue> {
            put("outcome", AnalyticsParamValue.StringValue(if (result is ApiResult.Success) "success" else "failure"))
            if (result is ApiResult.Error) {
                put("failure_code", AnalyticsParamValue.StringValue(result.code ?: "unknown"))
            }
        }
        analyticsClient?.log(AnalyticsEvent(name = eventName, params = params))
    }

    private val _uiState = MutableStateFlow(ProfileDashboardUiState())
    val uiState: StateFlow<ProfileDashboardUiState> = _uiState.asStateFlow()

    /**
     * Foreign profile target, resolved once from nav args. Null means "own profile" — the single
     * source of truth for which endpoint (getCurrentUser vs getUserById) a refresh should use,
     * instead of re-parsing savedStateHandle or inferring it from uiState.
     */
    private val targetUserId: UUID? = savedStateHandle.get<String>(Screen.Profile.ARG_USER_ID)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    /** The in-flight refreshAll() coroutine, if any — cancelled by a newer refresh so a stale
     * result can never land after (and overwrite) a more recent one. */
    private var refreshJob: Job? = null

    init {
        val rawUserId = savedStateHandle.get<String>(Screen.Profile.ARG_USER_ID)
        when {
            rawUserId != null && targetUserId == null ->
                _uiState.update { it.copy(errorMessage = "Invalid profile ID") }
            targetUserId == null -> {
                loadCurrentUser()
                // A push/inbox deep link always targets the recipient's own spot (D3) — consume
                // it only on the own-profile path.
                viewModelScope.launch {
                    val target = pendingDeepLink?.consume() ?: return@launch
                    val postId = target.postId ?: return@launch
                    openPostFromDeepLink(postId, target.openComments, category = target.destination.value)
                }
            }
            else -> {
                _uiState.update { it.copy(isOwnProfile = false) }
                loadForeignProfile(targetUserId)
            }
        }

        viewModelScope.launch {
            userRepository.currentUser.filterNotNull().collect { user ->
                _uiState.update {
                    it.copy(
                        user = if (targetUserId == null) user else it.user,
                        isCurrentUserAdmin = user.isAdmin,
                    )
                }
            }
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingUser = true) }
//            delay(2500) // TEMP: simulates a slow server for manual lag testing — remove after testing.
            when (val result = userRepository.getCurrentUser()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            user = result.data,
                            isLoadingUser = false,
                            isOwnProfile = true,
                            currentUserId = result.data.id,
                        )
                    }
                    loadFirstPage(result.data.id)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoadingUser = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun loadForeignProfile(targetUserId: UUID) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingUser = true) }
            val currentUserId = userPreferences.userId.first()
            when (val result = userRepository.getUserById(targetUserId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            user = result.data,
                            isLoadingUser = false,
                            currentUserId = currentUserId,
                            isOwnProfile = (targetUserId == currentUserId),
                        )
                    }
                    loadFirstPage(targetUserId)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoadingUser = false, errorMessage = result.message)
                }
            }
        }
    }

    /**
     * Fetches the current or foreign profile (per [targetUserId]) and applies the result to
     * [uiState]: success replaces the profile, failure surfaces a transient message without
     * touching the existing one. The single path for updating [ProfileDashboardUiState.user],
     * shared by refreshAll() and confirmDeletePost() — replaces the former standalone
     * refreshCurrentUser(), which silently dropped errors.
     */
    private suspend fun refreshUser(): ApiResult<User> {
        val result = if (targetUserId == null) {
            userRepository.getCurrentUser()
        } else {
            userRepository.getUserById(targetUserId)
        }
        when (result) {
            is ApiResult.Success -> _uiState.update {
                it.copy(user = result.data, currentUserId = it.currentUserId ?: result.data.id)
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(userMessage = "Couldn't refresh your profile. Please try again.")
            }
        }
        return result
    }

    private suspend fun loadFirstPage(userId: UUID) = load(userId, reset = true, isRefresh = false)

    fun refresh() {
        resetImageRetryState()
        refreshAll()
    }

    fun onPostCreated() {
        resetImageRetryState()
        refreshAll()
    }

    /**
     * Coordinated refresh: fetches the profile (current or foreign) and the first page of posts
     * together, so pull-to-refresh and post_created always see both updated in one cycle instead
     * of the posts page silently completing while the profile fetch is skipped or lost.
     */
    private fun refreshAll() {
        val state = _uiState.value
        val userId = state.user?.id
        refreshJob?.cancel()
        _uiState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            val userResult = refreshUser()

            val postsUserId = (userResult as? ApiResult.Success)?.data?.id ?: userId
            if (postsUserId != null) {
                // load() only turns isRefreshing off once its own fetch finishes; since it always
                // runs after the user fetch above, isRefreshing stays on for the whole cycle.
                // On a posts-fetch failure, load() leaves the existing posts list untouched.
                load(postsUserId, reset = true, isRefresh = true)
            } else {
                // No posts fetch will run (no known user id) — nothing left to clear isRefreshing,
                // and there's nothing on screen at all, so this is a blocking error, not a snackbar.
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = (userResult as? ApiResult.Error)?.message ?: it.errorMessage,
                    )
                }
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        val userId = state.user?.id ?: return
        if (!state.hasMore || state.isAnyLoading) return
        viewModelScope.launch { load(userId, reset = false, isRefresh = false) }
    }

    fun retry() {
        val state = _uiState.value
        val userId = state.user?.id ?: return
        if (state.isAnyLoading) return
        viewModelScope.launch { load(userId, reset = state.isEmpty, isRefresh = false) }
    }

    /**
     * Suspends until the posts page request completes, so a caller (refreshAll) that runs this
     * after another await can keep isRefreshing on for the whole coordinated cycle instead of
     * this function turning it off as soon as its own fetch — but not the sibling one — finishes.
     */
    private suspend fun load(userId: UUID, reset: Boolean, isRefresh: Boolean) {
        _uiState.update {
            it.copy(
                isLoadingInitial = reset && !isRefresh && it.isEmpty,
                isRefreshing = isRefresh,
                isLoadingMore = !reset,
                errorMessage = null,
            )
        }

        val cursor = if (reset) null else _uiState.value.nextCursor
//            delay(2500) // TEMP: simulates a slow server for manual lag testing — remove after testing.

        when (val result = postRepository.getUserPosts(userId, PAGE_SIZE, cursor)) {
            is ApiResult.Success -> _uiState.update { state ->
                val incoming = result.data.posts
                val merged = if (reset) {
                    incoming
                } else {
                    (state.posts + incoming).distinctBy { it.id }
                }
                val livePostIds = merged.mapTo(mutableSetOf()) { it.id }
                state.copy(
                    posts = merged,
                    nextCursor = result.data.nextCursor,
                    hasMore = result.data.hasMore,
                    isLoadingInitial = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    failedImages = state.failedImages.filterTo(mutableSetOf()) { it.postId in livePostIds },
                    imageRetryTokens = state.imageRetryTokens.filterKeys { it.postId in livePostIds },
                    autoRetriedImages = state.autoRetriedImages.filterTo(mutableSetOf()) { it.postId in livePostIds },
                    postDetailFetchedAt = if (reset) emptyMap() else state.postDetailFetchedAt,
                )
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(
                    isLoadingInitial = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    // ---- See-post overlay selection ----

    fun onPostClick(postId: UUID) {
        _uiState.update { it.copy(selectedPostId = postId) }
        refreshPostDetail(postId)
    }

    /**
     * Opens the see-post overlay for a post that may not be in [ProfileDashboardUiState.posts] —
     * the destination of a "like"/"comment" push deep link (D3), which can point at any spot on
     * the profile, not just one from the currently loaded page ([PAGE_SIZE]).
     * Falls back to [PostRepository.getPostDetail] and holds the result in
     * [ProfileDashboardUiState.deepLinkedPost] rather than inserting it into [ProfileDashboardUiState.posts],
     * so grid order and cursor pagination are never disturbed.
     *
     * A "comment" deep link also opens [ProfileDashboardUiState.commentsSheet] on top of the
     * overlay ([openComments] = true) — both are independently dismissible, so back closes the
     * sheet first and the overlay second, same as when a user opens comments manually. If the
     * commented-on comment itself was since deleted, the sheet just loads without it — no error.
     */
    fun openPostFromDeepLink(postId: UUID, openComments: Boolean = false, category: String? = null) {
        if (_uiState.value.posts.any { it.id == postId }) {
            onPostClick(postId)
            if (openComments) openCommentsSheet(postId)
            logDestinationReached(category, outcome = "ok")
            return
        }

        viewModelScope.launch {
            when (val result = postRepository.getPostDetail(postId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(selectedPostId = postId, deepLinkedPost = result.data) }
                    if (openComments) openCommentsSheet(postId)
                    logDestinationReached(category, outcome = "ok")
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(userMessage = "This spot isn't available anymore") }
                    logDestinationReached(category, outcome = if (result.code == "not_found") "not_found" else "error")
                }
            }
        }
    }

    /** Ev. push_destination_reached (§16, pas 7.2) — [category] is null when this open wasn't triggered by a deep link. */
    private fun logDestinationReached(category: String?, outcome: String) {
        if (category == null) return
        analyticsClient?.log(
            AnalyticsEvent(
                name = "push_destination_reached",
                params = mapOf(
                    "category" to AnalyticsParamValue.StringValue(category),
                    "outcome" to AnalyticsParamValue.StringValue(outcome),
                ),
            )
        )
    }

    /**
     * Refreshes engagement fields (like/comment counts, liked-by-current-user) for [postId] in the
     * background so the see-post overlay corrects stale counts without blocking on a request.
     * Deduped per post via [ProfileDashboardUiState.postDetailInFlight] and rate-limited via
     * [ProfileDashboardUiState.postDetailFetchedAt] so repeated opens within [DETAIL_TTL_MS] are free.
     */
    private fun refreshPostDetail(postId: UUID) {
        val state = _uiState.value
        if (postId in state.postDetailInFlight) return
        val lastFetchedAt = state.postDetailFetchedAt[postId]
        if (lastFetchedAt != null && System.currentTimeMillis() - lastFetchedAt < DETAIL_TTL_MS) return

        _uiState.update { it.copy(postDetailInFlight = it.postDetailInFlight + postId) }

        viewModelScope.launch {
            when (val result = postRepository.getPostDetail(postId)) {
                is ApiResult.Success -> {
                    val fresh = result.data
                    _uiState.update { s ->
                        s.copy(
                            posts = s.posts.replacePost(postId) { post ->
                                if (postId in s.likeInFlight) {
                                    post
                                } else {
                                    post.copy(
                                        likeCount = fresh.likeCount,
                                        commentCount = fresh.commentCount,
                                        likedByCurrentUser = fresh.likedByCurrentUser,
                                    )
                                }
                            },
                            postDetailInFlight = s.postDetailInFlight - postId,
                            postDetailFetchedAt = s.postDetailFetchedAt + (postId to System.currentTimeMillis()),
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update { s ->
                    s.copy(postDetailInFlight = s.postDetailInFlight - postId)
                }
            }
        }
    }

    fun clearSelectedPost() {
        _uiState.update { it.copy(selectedPostId = null, commentsSheet = null, deepLinkedPost = null) }
    }

    fun requestDeletePost() {
        if (!_uiState.value.isOwnProfile) return
        if (_uiState.value.selectedPostId == null) return
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDeletePost() {
        if (!_uiState.value.isOwnProfile) return
        val postId = _uiState.value.selectedPostId ?: return
        if (_uiState.value.deleteInFlight != null) return
        _uiState.update { it.copy(deleteInFlight = postId) }
        viewModelScope.launch {
            when (val result = postRepository.deletePost(postId)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            posts = state.posts.filterNot { it.id == postId },
                            selectedPostId = null,
                            commentsSheet = null,
                            showDeleteConfirm = false,
                            deleteInFlight = null,
                            postDetailInFlight = state.postDetailInFlight - postId,
                            postDetailFetchedAt = state.postDetailFetchedAt - postId,
                        )
                    }
                    postRemovalSignal.emit(PostRemovedEvent(postId, PostRemovalReason.SelfDelete))
                    refreshUser()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        showDeleteConfirm = false,
                        deleteInFlight = null,
                        userMessage = "Couldn't delete this post. Please try again.",
                    )
                }
            }
        }
    }

    fun consumeUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /**
     * Called after an admin successfully removes a post (see AdminViewModel.removePost). Evicts
     * it from the locally held post list immediately — this ViewModel has no persistent cache of
     * its own, so there is nothing else to invalidate — then reloads from the top.
     */
    fun onPostRemovedByAdmin(postId: UUID) {
        _uiState.update { state ->
            state.copy(
                posts = state.posts.filterNot { it.id == postId },
                postDetailInFlight = state.postDetailInFlight - postId,
                postDetailFetchedAt = state.postDetailFetchedAt - postId,
            )
        }
        viewModelScope.launch { postRemovalSignal.emit(PostRemovedEvent(postId, PostRemovalReason.Moderation)) }
        refresh()
    }

    fun showEarlySpotterInfo() {
        _uiState.update { it.copy(showEarlySpotterInfo = true) }
    }

    fun dismissEarlySpotterInfo() {
        _uiState.update { it.copy(showEarlySpotterInfo = false) }
    }

    // ---- Likes ----

    fun onLikeToggle(postId: UUID) {
        val current = _uiState.value.posts.firstOrNull { it.id == postId } ?: return
        if (postId in _uiState.value.likeInFlight) return

        val wasLiked = current.likedByCurrentUser

        _uiState.update { state ->
            state.copy(
                posts = state.posts.replacePost(postId) {
                    it.copy(
                        likedByCurrentUser = !wasLiked,
                        likeCount = (it.likeCount + if (wasLiked) -1 else 1).coerceAtLeast(0),
                    )
                },
                likeInFlight = state.likeInFlight + postId,
            )
        }

        viewModelScope.launch {
            val result = likeRepository.toggleLike(postId)
            logInteractionResult(EVENT_FEED_LIKE_RESULT, result)
            when (result) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(
                        posts = state.posts.replacePost(postId) {
                            it.copy(
                                likedByCurrentUser = result.data.liked,
                                likeCount = result.data.count,
                            )
                        },
                        likeInFlight = state.likeInFlight - postId,
                    )
                }

                is ApiResult.Error -> _uiState.update { state ->
                    state.copy(
                        posts = state.posts.replacePost(postId) {
                            it.copy(
                                likedByCurrentUser = wasLiked,
                                likeCount = (it.likeCount + if (wasLiked) 1 else -1).coerceAtLeast(0),
                            )
                        },
                        likeInFlight = state.likeInFlight - postId,
                        userMessage = "Couldn't update your like. Please try again.",
                    )
                }
            }
        }
    }

    // ---- Comments ----

    fun openComments(postId: UUID) {
        openCommentsSheet(postId)
    }

    private fun openCommentsSheet(postId: UUID) {
        _uiState.update { it.copy(commentsSheet = CommentsSheetState(postId = postId, isLoading = true)) }
        loadComments(postId)
    }

    fun closeComments() {
        _uiState.update { it.copy(commentsSheet = null) }
    }

    fun retryLoadComments() {
        val sheet = _uiState.value.commentsSheet ?: return
        _uiState.update { it.copy(commentsSheet = sheet.copy(isLoading = true, errorMessage = null)) }
        loadComments(sheet.postId)
    }

    private fun loadComments(postId: UUID) {
        viewModelScope.launch {
            val result = commentRepository.getCommentsForPost(postId)
            _uiState.update { state ->
                val sheet = state.commentsSheet?.takeIf { it.postId == postId } ?: return@update state
                state.copy(
                    commentsSheet = when (result) {
                        is ApiResult.Success -> sheet.copy(comments = result.data, isLoading = false, errorMessage = null)
                        is ApiResult.Error -> sheet.copy(isLoading = false, errorMessage = result.message)
                    },
                    posts = if (result is ApiResult.Success) {
                        state.posts.replacePost(postId) { it.copy(commentCount = result.data.size.toLong()) }
                    } else {
                        state.posts
                    },
                )
            }
        }
    }

    fun onCommentDraftChange(text: String) {
        _uiState.update { state ->
            val sheet = state.commentsSheet ?: return@update state
            state.copy(commentsSheet = sheet.copy(draft = text))
        }
    }

    fun submitComment() {
        val sheet = _uiState.value.commentsSheet ?: return
        val text = sheet.draft.trim()
        if (text.isEmpty() || sheet.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { state ->
                val s = state.commentsSheet ?: return@update state
                state.copy(commentsSheet = s.copy(isSubmitting = true))
            }

            val result = commentRepository.addComment(sheet.postId, text)
            logInteractionResult(EVENT_FEED_COMMENT_RESULT, result)
            when (result) {
                is ApiResult.Success -> _uiState.update { state ->
                    val s = state.commentsSheet?.takeIf { it.postId == sheet.postId }
                    state.copy(
                        commentsSheet = s?.copy(
                            comments = s.comments + result.data,
                            draft = "",
                            isSubmitting = false,
                        ) ?: state.commentsSheet,
                        posts = state.posts.replacePost(sheet.postId) {
                            it.copy(commentCount = it.commentCount + 1)
                        },
                    )
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

    // ---- Per-image retry (grid tiles) ----

    fun onImageLoadFailed(key: PostImageKey) {
        _uiState.update { it.copy(failedImages = it.failedImages + key) }
    }

    fun onImageLoadSucceeded(key: PostImageKey) {
        _uiState.update {
            it.copy(
                failedImages = it.failedImages - key,
                autoRetriedImages = it.autoRetriedImages - key,
                imageRetryTokens = it.imageRetryTokens - key,
            )
        }
    }

    fun retryImage(key: PostImageKey) {
        _uiState.update { state ->
            val nextToken = (state.imageRetryTokens[key] ?: 0) + 1
            state.copy(
                failedImages = state.failedImages - key,
                imageRetryTokens = state.imageRetryTokens + (key to nextToken),
            )
        }
    }

    fun retryFailedImagesOnce() {
        val state = _uiState.value
        val candidates = state.failedImages - state.autoRetriedImages
        if (candidates.isEmpty()) return
        candidates.forEach { retryImage(it) }
        _uiState.update { it.copy(autoRetriedImages = it.autoRetriedImages + candidates) }
    }

    private fun resetImageRetryState() {
        _uiState.update {
            it.copy(
                failedImages = emptySet(),
                imageRetryTokens = emptyMap(),
                autoRetriedImages = emptySet(),
            )
        }
    }

    companion object {
        private const val PAGE_SIZE = 15
        private const val DETAIL_TTL_MS = 30_000L
    }
}

private fun List<FeedPost>.replacePost(postId: UUID, transform: (FeedPost) -> FeedPost): List<FeedPost> =
    map { if (it.id == postId) transform(it) else it }
