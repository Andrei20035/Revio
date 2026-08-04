package com.revio.social.data.remote.dto.feedback

import com.revio.social.core.network.serialization.InstantSerializer
import com.revio.social.core.network.serialization.UUIDSerializer
import com.revio.social.data.model.ConfusionReason
import com.revio.social.data.model.FeedbackArea
import com.revio.social.data.model.FeedbackCategory
import com.revio.social.data.model.FeedbackPriority
import com.revio.social.data.model.FeedbackSource
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Body for `POST feedback/user`. Mirrors the server's `SubmitUserFeedbackDTO`. */
@Serializable
data class SubmitUserFeedbackRequest(
    val category: FeedbackCategory,
    val area: FeedbackArea? = null,
    val message: String? = null,
    val secondaryMessage: String? = null,
    val quickReason: ConfusionReason? = null,
    val priority: FeedbackPriority? = null,
    val rating: Int? = null,
    val keepMessage: String? = null,
    val improveMessage: String? = null,
    val source: FeedbackSource,
    val originScreen: String? = null,
    val includeDiagnostics: Boolean = false,
    val appVersion: String? = null,
    val androidVersion: String? = null,
    val deviceModel: String? = null,
    val connectionType: String? = null,
    val lastErrorCode: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val clientFeedbackId: UUID,
    @Serializable(with = InstantSerializer::class)
    val clientSubmittedAt: Instant? = null,
)
