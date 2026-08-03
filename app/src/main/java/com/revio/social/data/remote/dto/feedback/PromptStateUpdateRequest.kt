package com.revio.social.data.remote.dto.feedback

import com.revio.social.data.model.PromptEvent
import kotlinx.serialization.Serializable

/** Body for `POST feedback/prompt-state`. Mirrors the server's `PromptStateUpdateDTO`. */
@Serializable
data class PromptStateUpdateRequest(
    val promptKey: String,
    val event: PromptEvent,
)
