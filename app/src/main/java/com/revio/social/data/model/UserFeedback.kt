package com.revio.social.data.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Names are the wire contract and must stay identical to the backend's `FeedbackCategory` enum.
 */
@Serializable
enum class FeedbackCategory {
    NOT_WORKING,
    CONFUSING,
    FEATURE_IDEA,
    GENERAL,
}

@Serializable
enum class FeedbackArea {
    POSTING,
    FEED,
    PROFILE,
    ACTIVITY,
    LEADERBOARD,
    SETTINGS,
    AUTHENTICATION,
    NAVIGATION,
    NEW_AREA,
    NOT_SURE,
    OTHER,
}

@Serializable
enum class FeedbackPriority {
    NICE_TO_HAVE,
    IMPORTANT,
    BLOCKING,
}

@Serializable
enum class ConfusionReason {
    DIDNT_KNOW_WHAT_TO_DO_NEXT,
    WORDING_NOT_CLEAR,
    COULDNT_FIND_SOMETHING,
    UNEXPECTED_RESULT,
    TOO_MUCH_INFORMATION,
    OTHER,
}

@Serializable
enum class FeedbackSource {
    SETTINGS_FEEDBACK,
    FIRST_POST_PROMPT,
}

data class UserFeedbackPayload(
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
    val clientFeedbackId: UUID,
    val clientSubmittedAt: Instant? = null,
)
