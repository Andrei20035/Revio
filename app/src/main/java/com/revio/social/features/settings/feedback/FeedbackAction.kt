package com.revio.social.features.settings.feedback

import com.revio.social.data.model.ConfusionReason
import com.revio.social.data.model.FeedbackArea
import com.revio.social.data.model.FeedbackCategory
import com.revio.social.data.model.FeedbackPriority

sealed class FeedbackAction {
    data class SelectCategory(val category: FeedbackCategory) : FeedbackAction()

    data class MessageChanged(val text: String) : FeedbackAction()
    data class SecondaryMessageChanged(val text: String) : FeedbackAction()
    data class AreaSelected(val area: FeedbackArea?) : FeedbackAction()
    data class QuickReasonSelected(val reason: ConfusionReason?) : FeedbackAction()
    data class PrioritySelected(val priority: FeedbackPriority?) : FeedbackAction()
    data class RatingSelected(val rating: Int) : FeedbackAction()
    data class KeepMessageChanged(val text: String) : FeedbackAction()
    data class ImproveMessageChanged(val text: String) : FeedbackAction()
    data class ToggleIncludeDiagnostics(val enabled: Boolean) : FeedbackAction()

    object NextStep : FeedbackAction()
    object PreviousStep : FeedbackAction()

    object Submit : FeedbackAction()
    object Retry : FeedbackAction()
    object SendAnother : FeedbackAction()
    object Cancel : FeedbackAction() // navigating away is a UI concern
}
