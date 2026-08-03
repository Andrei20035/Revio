package com.revio.social.data.model

import kotlinx.serialization.Serializable
import java.time.Instant

const val FIRST_POST_FEEDBACK_KEY = "first_post_experience"

/**
 * Union of quick-reason chips across the three rating tiers (negative/neutral/positive).
 * Names are the wire contract and must stay identical to the backend's `QuickReason` enum.
 */
@Serializable
enum class QuickReason {
    UPLOAD_DIFFICULT,
    LOCATION_CONFUSING,
    CAR_DETAILS_CONFUSING,
    TOOK_TOO_LONG,
    SOMETHING_BROKE,
    UPLOAD_PROCESS,
    LOCATION,
    CAR_DETAILS,
    DESCRIPTION,
    POSTING_CONFIRMATION,
    EASY_TO_USE,
    FAST,
    CLEAR,
    FUN,
    LOOKS_GOOD,
    OTHER,
}

@Serializable
enum class PromptStatus {
    ELIGIBLE,
    DISMISSED_ONCE,
    DISMISSED_TWICE,
    SUBMITTED,
}

@Serializable
enum class FeedbackSurface {
    FEED,
    PROFILE,
}

@Serializable
enum class PromptEvent { SHOWN, DISMISSED }

data class FirstPostFeedbackPayload(
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
    val clientSubmittedAt: Instant? = null,
)

data class FeedbackPromptState(
    val promptKey: String,
    val status: PromptStatus,
    val shownCount: Int,
    val lastShownAt: Instant? = null,
)
