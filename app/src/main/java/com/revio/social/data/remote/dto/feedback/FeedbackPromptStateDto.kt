package com.revio.social.data.remote.dto.feedback

import com.revio.social.core.network.serialization.InstantSerializer
import com.revio.social.data.model.FeedbackPromptState
import com.revio.social.data.model.PromptStatus
import kotlinx.serialization.Serializable
import java.time.Instant

/** Response for `GET feedback/prompt-state`. Mirrors the server's `FeedbackPromptStateDTO`. */
@Serializable
data class FeedbackPromptStateDto(
    val promptKey: String,
    val status: PromptStatus,
    val shownCount: Int,
    @Serializable(with = InstantSerializer::class)
    val lastShownAt: Instant? = null,
)

fun FeedbackPromptStateDto.toDomain() = FeedbackPromptState(
    promptKey = promptKey,
    status = status,
    shownCount = shownCount,
    lastShownAt = lastShownAt,
)
