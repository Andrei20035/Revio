package com.revio.app.features.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.app.BuildConfig
import com.revio.app.core.network.ApiResult
import com.revio.app.data.model.FeedPost
import com.revio.app.data.model.ReportReason
import com.revio.app.data.repository.CommentRepository
import com.revio.app.data.repository.LikeRepository
import com.revio.app.data.repository.PostRepository
import com.revio.app.data.repository.ReportRepository
import com.revio.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val reportRepository: ReportRepository,
    private val likeRepository: LikeRepository,
    private val commentRepository: CommentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
        loadFirstPage()
        viewModelScope.launch {
            userRepository.currentUser.filterNotNull().collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    /** Initial load into an empty feed. */
    private fun loadFirstPage() = load(reset = true, isRefresh = false)

    /** Pull-to-refresh: reload from the top, keeping current content visible until it returns. */
    fun refresh() = load(reset = true, isRefresh = true)

    /** Infinite scroll: append the next page if there is one and nothing is already in flight. */
    fun loadNextPage() {
        val state = _uiState.value
        if (!state.hasMore || state.isAnyLoading) return
        load(reset = false, isRefresh = false)
    }

    /** Retry after an error — reloads the first page when empty, otherwise retries the next page. */
    fun retry() {
        val state = _uiState.value
        if (state.isAnyLoading) return
        load(reset = state.isEmpty, isRefresh = false)
    }

    private fun load(reset: Boolean, isRefresh: Boolean) {
        viewModelScope.launch {
            val shouldShowInitialLoading = reset && !isRefresh && _uiState.value.isEmpty
            _uiState.update {
                it.copy(
                    isLoadingInitial = reset && !isRefresh && it.isEmpty,
                    isRefreshing = isRefresh,
                    isLoadingMore = !reset,
                    errorMessage = null,
                )
            }

            if (BuildConfig.DEBUG && shouldShowInitialLoading) {
                delay(0)
            }

            val cursor = if (reset) null else _uiState.value.nextCursor

            when (val result = postRepository.getFeedPosts(limit = PAGE_SIZE, cursor = cursor)) {
                is ApiResult.Success -> _uiState.update { state ->
                    val incoming = result.data.posts
                    val merged = if (reset) {
                        incoming
                    } else {
                        // Append, de-duplicating on id in case a new post shifted the page window.
                        (state.feedPosts + incoming).distinctBy { it.id }
                    }
                    state.copy(
                        feedPosts = merged,
                        nextCursor = result.data.nextCursor,
                        hasMore = result.data.hasMore,
                        isLoadingInitial = false,
                        isRefreshing = false,
                        isLoadingMore = false,
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

        val wasLiked = current.likedByCurrentUser

        // Optimistic flip + count nudge, and mark the post as in-flight.
        _uiState.update { state ->
            state.copy(
                feedPosts = state.feedPosts.replacePost(postId) {
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
                    // Reconcile with the server's authoritative state.
                    state.copy(
                        feedPosts = state.feedPosts.replacePost(postId) {
                            it.copy(
                                likedByCurrentUser = result.data.liked,
                                likeCount = result.data.count,
                            )
                        },
                        likeInFlight = state.likeInFlight - postId,
                    )
                }

                is ApiResult.Error -> _uiState.update { state ->
                    // Revert the optimistic change.
                    state.copy(
                        feedPosts = state.feedPosts.replacePost(postId) {
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
            _uiState.update { state ->
                // Ignore if the sheet was closed or switched to another post meanwhile.
                val sheet = state.commentsSheet?.takeIf { it.postId == postId } ?: return@update state
                state.copy(
                    commentsSheet = when (result) {
                        is ApiResult.Success -> sheet.copy(comments = result.data, isLoading = false, errorMessage = null)
                        is ApiResult.Error -> sheet.copy(isLoading = false, errorMessage = result.message)
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

        viewModelScope.launch {
            _uiState.update { state ->
                val s = state.commentsSheet ?: return@update state
                state.copy(commentsSheet = s.copy(isSubmitting = true))
            }

            when (val result = commentRepository.addComment(sheet.postId, text)) {
                is ApiResult.Success -> _uiState.update { state ->
                    val s = state.commentsSheet?.takeIf { it.postId == sheet.postId }
                    state.copy(
                        // Append the new comment (server orders oldest-first) and clear the input.
                        commentsSheet = s?.copy(
                            comments = s.comments + result.data,
                            draft = "",
                            isSubmitting = false,
                        ) ?: state.commentsSheet,
                        // Keep the feed's comment count consistent with the new comment.
                        feedPosts = state.feedPosts.replacePost(sheet.postId) {
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
    }
}

/** Returns a new list with the post matching [postId] replaced by [transform]; others untouched. */
private fun List<FeedPost>.replacePost(postId: UUID, transform: (FeedPost) -> FeedPost): List<FeedPost> =
    map { if (it.id == postId) transform(it) else it }
