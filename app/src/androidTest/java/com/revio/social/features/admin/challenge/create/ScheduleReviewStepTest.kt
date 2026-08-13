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
    fun `Start now activ ascunde campul Starts`() {
        setContent(CreateChallengeUiState(form = CreateChallengeFormState(startNow = true)))

        composeTestRule.onNodeWithText("Ends").assertIsDisplayed()
        // Only the review summary's "Starts" row — the DateTimeField itself is hidden.
        composeTestRule.onAllNodesWithText("Starts").assertCountEquals(1)
    }

    @Test
    fun `Start now dezactivat afiseaza campul Starts`() {
        setContent(CreateChallengeUiState(form = CreateChallengeFormState(startNow = false)))

        // The DateTimeField's own label plus the review summary's "Starts" row.
        composeTestRule.onAllNodesWithText("Starts").assertCountEquals(2)
    }

    @Test
    fun `toggle Start now declanseaza SetStartNow`() {
        var lastAction: CreateChallengeAction? = null
        setContent(
            uiState = CreateChallengeUiState(form = CreateChallengeFormState(startNow = true)),
            onAction = { lastAction = it },
        )

        composeTestRule.onNodeWithTag("schedule_start_now_switch").performClick()

        assertEquals(CreateChallengeAction.SetStartNow(false), lastAction)
    }

    @Test
    fun `rezumatul afiseaza toate cele 8 randuri cu valorile corecte`() {
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
    fun `rezumatul afiseaza Now pentru Starts cand Start now e activ`() {
        setContent(
            CreateChallengeUiState(
                form = CreateChallengeFormState(startNow = true, endsAtLocal = LocalDateTime.of(2026, 8, 14, 9, 0)),
            ),
        )

        composeTestRule.onNodeWithText("Now").assertIsDisplayed()
    }

    @Test
    fun `peste 4 modele afiseaza rezumatul trunchiat cu N more`() {
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
    fun `eroare endsAt inainte de startsAt afiseaza mesajul inline`() {
        setContent(
            CreateChallengeUiState(fieldErrors = mapOf(CreateChallengeField.SCHEDULE to "End must be after start.")),
        )

        composeTestRule.onNodeWithText("End must be after start.").assertIsDisplayed()
    }

    @Test
    fun `eroare start in trecut afiseaza mesajul inline`() {
        setContent(
            CreateChallengeUiState(
                form = CreateChallengeFormState(startNow = false),
                fieldErrors = mapOf(CreateChallengeField.SCHEDULE to "Start time is in the past."),
            ),
        )

        composeTestRule.onNodeWithText("Start time is in the past.").assertIsDisplayed()
    }

    @Test
    fun `click pe Save draft declanseaza SaveDraft`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("Save draft").performClick()

        assertEquals(CreateChallengeAction.SaveDraft, lastAction)
    }

    @Test
    fun `click pe Publish challenge declanseaza RequestPublish`() {
        var lastAction: CreateChallengeAction? = null
        setContent(CreateChallengeUiState(), onAction = { lastAction = it })

        composeTestRule.onNodeWithText("Publish challenge").performClick()

        assertEquals(CreateChallengeAction.RequestPublish, lastAction)
    }

    @Test
    fun `succes partial afiseaza Draft saved but publishing failed si eroarea`() {
        setContent(
            CreateChallengeUiState(submitState = SubmitState.PartialSuccess("Server error")),
        )

        composeTestRule.onNodeWithText("Draft saved, but publishing failed.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server error").assertIsDisplayed()
    }

    @Test
    fun `succes partial reetichteaza butoanele Keep as draft si Retry publish`() {
        setContent(CreateChallengeUiState(submitState = SubmitState.PartialSuccess("Server error")))

        composeTestRule.onNodeWithText("Keep as draft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry publish").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save draft").assertDoesNotExist()
        composeTestRule.onNodeWithText("Publish challenge").assertDoesNotExist()
    }

    @Test
    fun `click pe Keep as draft declanseaza KeepAsDraft`() {
        var lastAction: CreateChallengeAction? = null
        setContent(
            uiState = CreateChallengeUiState(submitState = SubmitState.PartialSuccess("Server error")),
            onAction = { lastAction = it },
        )

        composeTestRule.onNodeWithText("Keep as draft").performClick()

        assertEquals(CreateChallengeAction.KeepAsDraft, lastAction)
    }

    @Test
    fun `click pe Retry publish declanseaza RetryPublish`() {
        var lastAction: CreateChallengeAction? = null
        setContent(
            uiState = CreateChallengeUiState(submitState = SubmitState.PartialSuccess("Server error")),
            onAction = { lastAction = it },
        )

        composeTestRule.onNodeWithText("Retry publish").performClick()

        assertEquals(CreateChallengeAction.RetryPublish, lastAction)
    }

    @Test
    fun `Failed afiseaza mesajul serverului deasupra butoanelor care raman Save draft si Publish challenge`() {
        setContent(CreateChallengeUiState(submitState = SubmitState.Failed("Server error")))

        composeTestRule.onNodeWithText("Server error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save draft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Publish challenge").assertIsDisplayed()
    }

    @Test
    fun `Submitting dezactiveaza ambele CTA`() {
        setContent(CreateChallengeUiState(submitState = SubmitState.Submitting))

        composeTestRule.onNodeWithText("Save draft").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Publish challenge").assertIsNotEnabled()
    }

    // ---------- Mod edit (Etapa 10) ----------

    @Test
    fun `mod edit afiseaza Save changes in loc de Save draft`() {
        setContent(CreateChallengeUiState(mode = CreateChallengeMode.Edit(UUID.randomUUID())))

        composeTestRule.onNodeWithText("Save changes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save draft").assertDoesNotExist()
    }

    @Test
    fun `conflict de lifecycle afiseaza un singur buton Open details`() {
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
