package com.revio.app.features.settings.deleteaccount

import com.revio.app.data.model.AuthProvider
import com.revio.app.data.remote.dto.auth.DeletionContextDto

sealed class DeleteAccountStep {
    object Reason : DeleteAccountStep()
    object Retention : DeleteAccountStep()
    object Confirm : DeleteAccountStep()
}

/** Names mirror the server's `DeletionReason` enum (V18 migration) — keep them in sync. */
enum class DeletionReason {
    TOO_MANY_NOTIFICATIONS,
    NOT_INTERESTING_CARSPOTS,
    FOUND_BETTER_APP,
    PRIVACY_CONCERNS,
    TAKING_A_BREAK,
    OTHER,
}

data class DeleteAccountUiState(
    val currentStep: DeleteAccountStep = DeleteAccountStep.Reason,
    val selectedReason: DeletionReason? = null,
    val otherText: String = "",

    val stats: DeletionContextDto? = null,
    val provider: AuthProvider? = null,

    val password: String = "",
    val usernameConfirmation: String = "",
    val confirmFieldError: String? = null,
    val generalError: String? = null,

    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val deletionCompleted: Boolean = false,
)
