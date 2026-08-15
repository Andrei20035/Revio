package com.revio.social.features.admin.challenge.create

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.data.model.AdminCarFamily
import com.revio.social.data.remote.dto.car_model.CarModelOption
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins [ScheduleReviewStep]'s `Start now` toggle, the review summary's 8 rows, and that a
 * [CreateChallengeField.SCHEDULE] error renders inline. Doesn't drive the platform
 * `DatePickerDialog`/`TimePickerDialog` themselves — per the plan's own risk note, [DateTimeField]
 * is tested through the state it produces, not the dialog. Exercises the stateless composable
 * directly, no Hilt.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleReviewStepTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val family = AdminCarFamily(
        id = UUID.fromString("00000000-0000-0000-0000-0000000000f1"),
        brand = "Volkswagen",
        name = "Golf",
    )

    private fun setContent(uiState: CreateChallengeUiState, onAction: (CreateChallengeAction) -> Unit = {}) {
        composeTestRule.setContent { ScheduleReviewStep(uiState = uiState, onAction = onAction) }
    }

    @Test
    fun `Start_now_activ_ascunde_campul_Starts`() {
        setContent(CreateChallengeUiState(form = CreateChallengeFormState(startNow = true)))

        composeTestRule.onNodeWithText("Ends").assertIsDisplayed()
        // Only the review summary's "Starts" row — the DateTimeField itself is hidden.
        composeTestRule.onAllNodesWithText("Starts").assertCountEquals(1)
    }

    @Test
    fun `Start_now_dezactivat_afiseaza_campul_Starts`() {
        setContent(CreateChallengeUiState(form = CreateChallengeFormState(startNow = false)))

        // The DateTimeField's own label plus the review summary's "Starts" row.
        composeTestRule.onAllNodesWithText("Starts").assertCountEquals(2)
    }

    @Test
    fun `toggle_Start_now_declanseaza_SetStartNow`() {
        var lastAction: CreateChallengeAction? = null
        setContent(
            uiState = CreateChallengeUiState(form = CreateChallengeFormState(startNow = true)),
            onAction = { lastAction = it },
        )

        composeTestRule.onNodeWithTag("schedule_start_now_switch").performClick()

        assertEquals(CreateChallengeAction.SetStartNow(false), lastAction)
    }

    @Test
    fun `rezumatul_afiseaza_toate_cele_8_randuri_cu_valorile_corecte`() {
        val models = listOf(
            CarModelOption(id = UUID.randomUUID(), model = "Golf"),
            CarModelOption(id = UUID.randomUUID(), model = "Golf Plus"),
        )
        val uiState = CreateChallengeUiState(
            form = CreateChallengeFormState(
                selectedBrand = family.brand,
                selectedFamilyId = family.id,
                title = "Weekend Golf Hunt",
                requiredPostsInput = "5",
                rewardPointsInput = "300",
                startNow = false,
                startsAtLocal = LocalDateTime.of(2026, 8, 12, 9, 0),
                endsAtLocal = LocalDateTime.of(2026, 8, 14, 9, 0),
            ),
            familiesState = FamiliesState.Content(listOf(family)),
            modelsState = ModelsState.Content(models),
        )

        setContent(uiState)

        composeTestRule.onNodeWithText("Weekend Golf Hunt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Volkswagen · Golf").assertIsDisplayed()
        composeTestRule.onNodeWithText("Golf, Golf Plus").assertIsDisplayed()
        composeTestRule.onNodeWithText("5").assertIsDisplayed()
        composeTestRule.onNodeWithText("300 pts").assertIsDisplayed()
        // Each date/time value appears twice — once in its DateTimeField, once in the summary row.
        composeTestRule.onAllNodesWithText("12 Aug 2026, 09:00").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("14 Aug 2026, 09:00").assertCountEquals(2)
        composeTestRule.onNodeWithText(ZoneId.systemDefault().id).assertIsDisplayed()
    }

    @Test
    fun `rezumatul_afiseaza_Now_pentru_Starts_cand_Start_now_e_activ`() {
        setContent(
            CreateChallengeUiState(
                form = CreateChallengeFormState(startNow = true, endsAtLocal = LocalDateTime.of(2026, 8, 14, 9, 0)),
            ),
        )

        composeTestRule.onNodeWithText("Now").assertIsDisplayed()
    }

    @Test
    fun `peste_4_modele_afiseaza_rezumatul_trunchiat_cu_N_more`() {
        val models = (1..6).map { CarModelOption(id = UUID.randomUUID(), model = "Model $it") }
        setContent(
            CreateChallengeUiState(
                form = CreateChallengeFormState(selectedBrand = family.brand, selectedFamilyId = family.id),
                familiesState = FamiliesState.Content(listOf(family)),
                modelsState = ModelsState.Content(models),
            ),
        )

        composeTestRule.onNodeWithText("Model 1, Model 2, Model 3, Model 4 +2 more").assertIsDisplayed()
    }

    @Test
    fun `eroare_endsAt_inainte_de_startsAt_afiseaza_mesajul_inline`() {
        setContent(
            CreateChallengeUiState(fieldErrors = mapOf(CreateChallengeField.SCHEDULE to "End must be after start.")),
        )

        composeTestRule.onNodeWithText("End must be after start.").assertIsDisplayed()
    }

    @Test
    fun `eroare_start_in_trecut_afiseaza_mesajul_inline`() {
        setContent(
            CreateChallengeUiState(
                form = CreateChallengeFormState(startNow = false),
                fieldErrors = mapOf(CreateChallengeField.SCHEDULE to "Start time is in the past."),
            ),
        )

        composeTestRule.onNodeWithText("Start time is in the past.").assertIsDisplayed()
    }

    @Test
    fun `click_pe_Save_draft_declanseaza_SaveDraft`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("Save draft").performClick()

        assertEquals(CreateChallengeAction.SaveDraft, lastAction)
    }

    @Test
    fun `click_pe_Publish_challenge_declanseaza_RequestPublish`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("Publish challenge").performClick()

        assertEquals(CreateChallengeAction.RequestPublish, lastAction)
    }

    @Test
    fun `succes_partial_afiseaza_Draft_saved_but_publishing_failed_si_eroarea`() {
        setContent(
            CreateChallengeUiState(submitState = SubmitState.PartialSuccess("Server error")),
        )

        composeTestRule.onNodeWithText("Draft saved, but publishing failed.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server error").assertIsDisplayed()
    }

    @Test
    fun `succes_partial_reetichteaza_butoanele_Keep_as_draft_si_Retry_publish`() {
        setContent(CreateChallengeUiState(submitState = SubmitState.PartialSuccess("Server error")))

        composeTestRule.onNodeWithText("Keep as draft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry publish").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save draft").assertDoesNotExist()
        composeTestRule.onNodeWithText("Publish challenge").assertDoesNotExist()
    }

    @Test
    fun `click_pe_Keep_as_draft_declanseaza_KeepAsDraft`() {
        var lastAction: CreateChallengeAction? = null
        setContent(
            uiState = CreateChallengeUiState(submitState = SubmitState.PartialSuccess("Server error")),
            onAction = { lastAction = it },
        )

        composeTestRule.onNodeWithText("Keep as draft").performClick()

        assertEquals(CreateChallengeAction.KeepAsDraft, lastAction)
    }

    @Test
    fun `click_pe_Retry_publish_declanseaza_RetryPublish`() {
        var lastAction: CreateChallengeAction? = null
        setContent(
            uiState = CreateChallengeUiState(submitState = SubmitState.PartialSuccess("Server error")),
            onAction = { lastAction = it },
        )

        composeTestRule.onNodeWithText("Retry publish").performClick()

        assertEquals(CreateChallengeAction.RetryPublish, lastAction)
    }

    @Test
    fun `Failed_afiseaza_mesajul_serverului_deasupra_butoanelor_care_raman_Save_draft_si_Publish_challenge`() {
        setContent(CreateChallengeUiState(submitState = SubmitState.Failed("Server error")))

        composeTestRule.onNodeWithText("Server error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save draft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Publish challenge").assertIsDisplayed()
    }

    @Test
    fun `Submitting_dezactiveaza_ambele_CTA`() {
        setContent(CreateChallengeUiState(submitState = SubmitState.Submitting))

        composeTestRule.onNodeWithText("Save draft").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Publish challenge").assertIsNotEnabled()
    }

    // ---------- Mod edit (Etapa 10) ----------

    @Test
    fun `mod_edit_afiseaza_Save_changes_in_loc_de_Save_draft`() {
        setContent(CreateChallengeUiState(mode = CreateChallengeMode.Edit(UUID.randomUUID())))

        composeTestRule.onNodeWithText("Save changes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save draft").assertDoesNotExist()
    }

    @Test
    fun `conflict_de_lifecycle_afiseaza_un_singur_buton_Open_details`() {
        var lastAction: CreateChallengeAction? = null
        val challengeId = UUID.randomUUID()
        setContent(
            uiState = CreateChallengeUiState(
                mode = CreateChallengeMode.Edit(challengeId),
                createdDraftId = challengeId,
                submitState = SubmitState.Failed("This challenge was already published."),
            ),
            onAction = { lastAction = it },
        )

        composeTestRule.onNodeWithText("This challenge was already published.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open details").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save changes").assertDoesNotExist()
        composeTestRule.onNodeWithText("Publish challenge").assertDoesNotExist()

        composeTestRule.onNodeWithText("Open details").performClick()

        assertEquals(CreateChallengeAction.KeepAsDraft, lastAction)
    }
}
