package com.revio.social.core.feedback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Emitted once a new post (never an edit) has been confirmed by the server. */
data class PostCreatedEvent(
    val postId: UUID,
    val uploadDurationMs: Long,
    val retryCount: Int,
    val lastErrorCode: String?,
)

/**
 * App-scoped signal for "a new post was just confirmed by the server". Unlike the
 * `savedStateHandle["post_created"]` flag (visible only to the screen that launched the
 * upload), this is observable from anywhere — e.g. Feed and Profile both need it to arm the
 * first-post feedback prompt regardless of which screen the user posted from.
 */
@Singleton
class PostCreationSignal @Inject constructor() {
    private val _events = MutableSharedFlow<PostCreatedEvent>(replay = 1)
    val events: SharedFlow<PostCreatedEvent> = _events

    suspend fun emit(event: PostCreatedEvent) {
        _events.emit(event)
    }
}
