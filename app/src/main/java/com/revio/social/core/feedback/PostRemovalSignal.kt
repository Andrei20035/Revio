package com.revio.social.core.feedback

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Why a post left the system — mirrors the server's [PostRemovalActor] distinction. */
enum class PostRemovalReason {
    SelfDelete,
    Moderation,
}

/** Emitted once a post has been confirmed removed by the server (self-delete or moderation). */
data class PostRemovedEvent(
    val postId: UUID,
    val reason: PostRemovalReason,
)

/**
 * App-scoped signal for "a post was just confirmed removed by the server". Mirrors
 * [PostCreationSignal] but for the opposite lifecycle event — consumers that recompute
 * challenge/feed progress on post creation need the same invalidation on removal.
 *
 * Uses `replay = 0`: a collector created after the emission already runs its own fetch on
 * `init`, so replaying the event would only cause a redundant refresh.
 */
@Singleton
class PostRemovalSignal @Inject constructor() {
    private val _events = MutableSharedFlow<PostRemovedEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<PostRemovedEvent> = _events

    suspend fun emit(event: PostRemovedEvent) {
        _events.emit(event)
    }
}
