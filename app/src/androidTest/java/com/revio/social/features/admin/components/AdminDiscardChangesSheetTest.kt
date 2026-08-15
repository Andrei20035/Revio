package com.revio.social.features.admin.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins [AdminDiscardChangesSheet]'s two copy/color variants: no draft yet ("Discard this
 * challenge?" / "Discard") vs. a draft already saved ("Leave without publishing?" / "Leave") —
 * the create-challenge wizard's back/discard confirmation (plan §5). Stateless, no Hilt.
 */
@RunWith(AndroidJUnit4::class)
class AdminDiscardChangesSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `fara_draft_afiseaza_Discard_this_challenge_si_Your_changes_wont_be_saved`() {
        composeTestRule.setContent {
            AdminDiscardChangesSheet(hasSavedDraft = false, onKeepEditing = {}, onDiscard = {})
        }

        composeTestRule.onNodeWithText("Discard this challenge?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your changes won't be saved.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").assertIsDisplayed()
    }

    @Test
    fun `cu_draft_salvat_afiseaza_Leave_without_publishing_si_Your_draft_is_saved`() {
        composeTestRule.setContent {
            AdminDiscardChangesSheet(hasSavedDraft = true, onKeepEditing = {}, onDiscard = {})
        }

        composeTestRule.onNodeWithText("Leave without publishing?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your draft is saved. It just isn't published yet.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Leave").assertIsDisplayed()
    }

    @Test
    fun `click_pe_Keep_editing_declanseaza_onKeepEditing`() {
        var kept = false
        composeTestRule.setContent {
            AdminDiscardChangesSheet(hasSavedDraft = false, onKeepEditing = { kept = true }, onDiscard = {})
        }

        composeTestRule.onNodeWithText("Keep editing").performClick()

        assertTrue(kept)
    }

    @Test
    fun `fara_draft_click_pe_Discard_declanseaza_onDiscard`() {
        var discarded = false
        composeTestRule.setContent {
            AdminDiscardChangesSheet(hasSavedDraft = false, onKeepEditing = {}, onDiscard = { discarded = true })
        }

        composeTestRule.onNodeWithText("Discard").performClick()

        assertTrue(discarded)
    }

    @Test
    fun `cu_draft_salvat_click_pe_Leave_declanseaza_onDiscard`() {
        var discarded = false
        composeTestRule.setContent {
            AdminDiscardChangesSheet(hasSavedDraft = true, onKeepEditing = {}, onDiscard = { discarded = true })
        }

        composeTestRule.onNodeWithText("Leave").performClick()

        assertTrue(discarded)
    }
}
