package com.revio.social.data.remote.dto.feedback

import com.revio.social.core.network.serialization.InstantSerializer
import com.revio.social.data.model.FeedbackSurface
import com.revio.social.data.model.QuickReason
import kotlinx.serialization.Serializable
import java.time.Instant

/** Body for `POST feedback/first-post`. Mirrors the server's `SubmitFirstPostFeedbackDTO`. */
@Serializable
data class SubmitFirstPostFeedbackRequest(
    val rating: Int,
    val quickReason: QuickReason? = null,
    val comment: String? = null,
    val surface: FeedbackSurface? = null,
    val appVersion: String? = null,
    val androidVersion: String? = null,
    val deviceModel: String? = null,
    val connectionType: String? = null,
    val uploadDurationMs: Int? = null,
    val hadRetries: Boolean? = null,
    val lastErrorCode: String? = null,
    @Serializable(with = InstantSerializer::class)
    val clientSubmittedAt: Instant? = null,
)
