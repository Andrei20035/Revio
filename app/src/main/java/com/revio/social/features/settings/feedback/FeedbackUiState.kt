package com.revio.social.features.settings.feedback

import com.revio.social.data.model.ConfusionReason
import com.revio.social.data.model.FeedbackArea
import com.revio.social.data.model.FeedbackCategory
import com.revio.social.data.model.FeedbackPriority
import java.util.UUID

sealed class FeedbackStep {
    object CategoryPicker : FeedbackStep()
    object Form : FeedbackStep()
    object Review : FeedbackStep()
    object Sent : FeedbackStep()
}

data class FeedbackUiState(
    val step: FeedbackStep = FeedbackStep.CategoryPicker,
    val category: FeedbackCategory? = null,

    val message: String = "",
    val secondaryMessage: String = "",
    val area: FeedbackArea? = null,
    val quickReason: ConfusionReason? = null,
    val priority: FeedbackPriority? = null,
    val rating: Int? = null,
    val keepMessage: String = "",
    val improveMessage: String = "",
    val includeDiagnostics: Boolean = true,

    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,

    /** Generated once per form and kept stable across retries so resubmission is idempotent. */
    val clientFeedbackId: UUID = UUID.randomUUID(),
) {
    val canSubmit: Boolean
        get() = when (category) {
            null -> false
            FeedbackCategory.NOT_WORKING -> message.isNotBlank()
            FeedbackCategory.GENERAL -> message.isNotBlank() || (rating != null && quickReason != null)
            FeedbackCategory.CONFUSING, FeedbackCategory.FEATURE_IDEA -> message.isNotBlank()
        }
}
