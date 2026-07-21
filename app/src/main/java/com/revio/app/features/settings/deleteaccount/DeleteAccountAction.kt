package com.revio.app.features.settings.deleteaccount

sealed class DeleteAccountAction {
    data class SelectReason(val reason: DeletionReason) : DeleteAccountAction()
    data class OtherTextChanged(val text: String) : DeleteAccountAction()

    object NextStep : DeleteAccountAction()
    object PreviousStep : DeleteAccountAction()

    data class PasswordChanged(val value: String) : DeleteAccountAction()
    data class UsernameConfirmationChanged(val value: String) : DeleteAccountAction()

    object Confirm : DeleteAccountAction()
    object KeepAccount : DeleteAccountAction()
}
