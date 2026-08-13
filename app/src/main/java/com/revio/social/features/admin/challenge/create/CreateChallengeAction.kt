package com.revio.social.features.admin.challenge.create

import java.time.LocalDateTime
import java.util.UUID

sealed interface CreateChallengeAction {

    // Step 1 — Vehicle
    data class SelectBrand(val brand: String) : CreateChallengeAction
    data class SelectFamily(val familyId: UUID) : CreateChallengeAction
    data object RetryLoadFamilies : CreateChallengeAction
    data object RetryLoadModels : CreateChallengeAction

    // Step 2 — Goal and reward
    data class UpdateTitle(val value: String) : CreateChallengeAction
    data class UpdateDescription(val value: String) : CreateChallengeAction
    data class UpdateRequiredPosts(val value: String) : CreateChallengeAction
    data class UpdateRewardPoints(val value: String) : CreateChallengeAction

    // Step 3 — Schedule and review
    data class SetStartNow(val startNow: Boolean) : CreateChallengeAction
    data class UpdateStartsAt(val value: LocalDateTime) : CreateChallengeAction
    data class UpdateEndsAt(val value: LocalDateTime) : CreateChallengeAction

    // Step navigation
    data object NextStep : CreateChallengeAction
    data object PreviousStep : CreateChallengeAction

    // Submit
    data object SaveDraft : CreateChallengeAction
    data object RequestPublish : CreateChallengeAction
    data object ConfirmPublish : CreateChallengeAction
    data object DismissPublishSheet : CreateChallengeAction

    /** After create-succeeded-but-publish-failed: re-sends an update only if the form changed
     * since the last submit, then retries publish. Never re-creates the draft. */
    data object RetryPublish : CreateChallengeAction

    /** After a partial success, leaves the already-created draft as-is and navigates to it. */
    data object KeepAsDraft : CreateChallengeAction

    // Back / discard
    data object RequestClose : CreateChallengeAction
    data object ConfirmDiscard : CreateChallengeAction
    data object DismissDiscardSheet : CreateChallengeAction
}
