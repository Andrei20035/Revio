package com.revio.app.features.profile.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.app.core.navigation.Screen
import com.revio.app.core.network.ApiResult
import com.revio.app.data.local.preferences.UserPreferences
import com.revio.app.data.model.FeedPost
import com.revio.app.data.repository.CommentRepository
import com.revio.app.data.repository.LikeRepository
import com.revio.app.data.repository.PostRepository
import com.revio.app.data.repository.UserRepository
import com.revio.app.features.feed.CommentsSheetState
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class ProfileDashboardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val likeRepository: LikeRepository,
    private val commentRepository: CommentRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileDashboardUiState())
    val uiState: StateFlow<ProfileDashboardUiState> = _uiState.asStateFlow()

    init {
        val rawUserId = savedStateHandle.get<String>(Screen.Profile.ARG_USER_ID)
        val targetUserId = rawUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        when {
            rawUserId != null && targetUserId == null ->
                _uiState.update { it.copy(errorMessage = "Invalid profile ID") }
            targetUserId == null ->
                loadCurrentUser()
            else -> {
                _uiState.update { it.copy(isOwnProfile = false) }
                loadForeignProfile(targetUserId)
            }
        }

        viewModelScope.launch {
            userRepository.currentUser.filterNotNull().collect { user ->
                if (_uiState.value.isOwnProfile) {
                    _uiState.update { it.copy(user = user) }
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

    private fun refreshCurrentUser() {
        viewModelScope.launch {
            when (val result = userRepository.getCurrentUser()) {
                is ApiResult.Success -> _uiState.update { it.copy(user = result.data) }
                is ApiResult.Error -> Unit
            }
        }
    }

    private fun loadFirstPage(userId: UUID) = load(userId, reset = true, isRefresh = false)

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
        viewModelScope.launch {
            val userResult = if (state.isOwnProfile) {
                userRepository.getCurrentUser()
            } else {
                val rawUserId = savedStateHandle.get<String>(Screen.Profile.ARG_USER_ID)
                val targetUserId = rawUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                targetUserId?.let { userRepository.getUserById(it) }
            }

            when (userResult) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(user = userResult.data, currentUserId = it.currentUserId ?: userResult.data.id)
                }
                is ApiResult.Error -> Unit
                null -> Unit
            }

            val postsUserId = (userResult as? ApiResult.Success)?.data?.id ?: userId
            if (postsUserId != null) {
                load(postsUserId, reset = true, isRefresh = true)
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        val userId = state.user?.id ?: return
        if (!state.hasMore || state.isAnyLoading) return
        load(userId, reset = false, isRefresh = false)
    }

    fun retry() {
        val state = _uiState.value
        val userId = state.user?.id ?: return
        if (state.isAnyLoading) return
        load(userId, reset = state.isEmpty, isRefresh = false)
    }

    private fun load(userId: UUID, reset: Boolean, isRefresh: Boolean) {
        viewModelScope.launch {
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
    }

    // ---- See-post overlay selection ----

    fun onPostClick(postId: UUID) {
        _uiState.update { it.copy(selectedPostId = postId) }
        refreshPostDetail(postId)
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
        _uiState.update { it.copy(selectedPostId = null, commentsSheet = null) }
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
                    refreshCurrentUser()
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
            when (val result = likeRepository.toggleLike(postId)) {
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

            when (val result = commentRepository.addComment(sheet.postId, text)) {
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
