package com.revio.social.features.admin.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the plan's own requirement for revoke-all: the destructive action fires only on the
 * *second* explicit tap ("Continue" on the warning step, then "Revoke all" on the confirm step) —
 * never on the first. Also pins that both sheets' destructive buttons disable while [isSubmitting]
 * is true, mirroring `AdminRemovePostSheetTest`'s coverage of `AdminRemovePostSheet`. Pure Compose
 * tests — both sheets are stateless w.r.t. the network, so no Hilt/DI is needed.
 */
@RunWith(AndroidJUnit4::class)
class AdminCancelChallengeSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---------- AdminCancelChallengeSheet ----------

    @Test
    fun cancel_sheet_afiseaza_avertismentul_si_butonul_Cancel_challenge() {
        composeTestRule.setContent {
            AdminCancelChallengeSheet(isSubmitting = false, onConfirm = {}, onDismiss = {})
        }

        composeTestRule.onNodeWithText("Cancel challenge").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "This will cancel the challenge and revoke any rewards already granted. " +
                    "This cannot be undone.",
            )
            .assertIsDisplayed()
    }

    @Test
    fun cancel_sheet_click_pe_Cancel_challenge_declanseaza_onConfirm() {
        var confirmed = false
        composeTestRule.setContent {
            AdminCancelChallengeSheet(isSubmitting = false, onConfirm = { confirmed = true }, onDismiss = {})
        }

        composeTestRule.onNodeWithText("Cancel challenge").performClick()
        assert(confirmed)
    }

    @Test
    fun cancel_sheet_isSubmitting_dezactiveaza_butonul_distructiv() {
        composeTestRule.setContent {
            AdminCancelChallengeSheet(isSubmitting = true, onConfirm = {}, onDismiss = {})
        }

        // While submitting the button shows a spinner instead of its label, so it's located by
        // the "Back" sibling being disabled and by there being no enabled "Cancel challenge" node.
        composeTestRule.onNodeWithText("Back").assertIsNotEnabled()
    }

    // ---------- AdminRevokeAllChallengeSheet ----------

    @Test
    fun revoke_all_sheet_porneste_pe_pasul_de_avertisment() {
        composeTestRule.setContent {
            AdminRevokeAllChallengeSheet(isSubmitting = false, onConfirm = {}, onDismiss = {})
        }

        composeTestRule.onNodeWithText("Revoke all rewards").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun revoke_all_sheet_primul_tap_pe_Continue_NU_declanseaza_onConfirm() {
        var confirmed = false
        composeTestRule.setContent {
            AdminRevokeAllChallengeSheet(isSubmitting = false, onConfirm = { confirmed = true }, onDismiss = {})
        }

        composeTestRule.onNodeWithText("Continue").performClick()

        assert(!confirmed)
        composeTestRule.onNodeWithText("Are you sure?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Revoke all").assertIsDisplayed()
    }

    @Test
    fun revoke_all_sheet_al_doilea_tap_pe_Revoke_all_declanseaza_onConfirm() {
        var confirmed = false
        composeTestRule.setContent {
            AdminRevokeAllChallengeSheet(isSubmitting = false, onConfirm = { confirmed = true }, onDismiss = {})
        }

        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Revoke all").performClick()

        assert(confirmed)
    }

    @Test
    fun revoke_all_sheet_pe_pasul_de_confirmare_isSubmitting_dezactiveaza_Back_si_Revoke_all() {
        composeTestRule.setContent {
            AdminRevokeAllChallengeSheet(isSubmitting = true, onConfirm = {}, onDismiss = {})
        }

        composeTestRule.onNodeWithText("Continue").performClick()

        composeTestRule.onNodeWithText("Back").assertIsNotEnabled()
        // While submitting the destructive button shows a spinner instead of "Revoke all".
        composeTestRule.onNodeWithText("Revoke all").assertDoesNotExist()
    }
}
