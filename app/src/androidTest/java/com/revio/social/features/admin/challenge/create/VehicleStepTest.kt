package com.revio.social.features.admin.challenge.create

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.data.model.AdminCarFamily
import com.revio.social.data.remote.dto.car_model.CarModelOption
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins [VehicleStep]'s four [FamiliesState] branches (loading/empty/error/content — the same
 * convention as `AdminChallengesScreen.kt:101-129`), the "included models" preview, and the
 * empty-family `Next`-blocking rule the plan's §5 confirms with the user. Exercises the stateless
 * composable directly, no Hilt — mirrors `AdminChallengesScreenTest`.
 */
@RunWith(AndroidJUnit4::class)
class VehicleStepTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val family = AdminCarFamily(
        id = UUID.fromString("00000000-0000-0000-0000-0000000000f1"),
        brand = "Volkswagen",
        name = "Golf",
    )
    private val models = listOf(
        CarModelOption(id = UUID.fromString("00000000-0000-0000-0000-0000000000e1"), model = "Golf"),
        CarModelOption(id = UUID.fromString("00000000-0000-0000-0000-0000000000e2"), model = "Golf Plus"),
    )

    private fun setContent(uiState: CreateChallengeUiState, onAction: (CreateChallengeAction) -> Unit = {}) {
        composeTestRule.setContent { VehicleStep(uiState = uiState, onAction = onAction) }
    }

    @Test
    fun `familiesState_Loading_afiseaza_indicatorul_de_progres`() {
        setContent(CreateChallengeUiState(familiesState = FamiliesState.Loading))

        composeTestRule.onNodeWithTag("create_challenge_loading").assertIsDisplayed()
    }

    @Test
    fun `familiesState_Empty_afiseaza_mesajul_No_car_families_yet`() {
        setContent(CreateChallengeUiState(familiesState = FamiliesState.Empty))

        composeTestRule.onNodeWithText("No car families yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create a family from the server admin tools first.").assertIsDisplayed()
    }

    @Test
    fun `familiesState_Error_afiseaza_mesajul_si_Retry_declanseaza_RetryLoadFamilies`() {
        var retried = false
        setContent(
            uiState = CreateChallengeUiState(
                familiesState = FamiliesState.Error("Server error", isOffline = false),
            ),
            onAction = { if (it == CreateChallengeAction.RetryLoadFamilies) retried = true },
        )

        composeTestRule.onNodeWithText("Couldn't load car families").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `familiesState_Error_de_retea_afiseaza_OfflineStateMessage`() {
        setContent(
            CreateChallengeUiState(familiesState = FamiliesState.Error("Network error", isOffline = true)),
        )

        composeTestRule.onNodeWithText("You're not connected to the internet").assertIsDisplayed()
    }

    @Test
    fun `familiesState_Content_afiseaza_selectoarele_Brand_si_Model_family`() {
        setContent(CreateChallengeUiState(familiesState = FamiliesState.Content(listOf(family))))

        composeTestRule.onNodeWithText("Brand").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select a brand").assertIsDisplayed()
        composeTestRule.onNodeWithText("Model family").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select a family").assertIsDisplayed()
    }

    @Test
    fun `familie_selectata_cu_modele_afiseaza_preview-ul_Included_models`() {
        setContent(
            CreateChallengeUiState(
                form = CreateChallengeFormState(selectedBrand = family.brand, selectedFamilyId = family.id),
                familiesState = FamiliesState.Content(listOf(family)),
                modelsState = ModelsState.Content(models),
            ),
        )

        composeTestRule.onNodeWithTag("create_challenge_models_preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Included models (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Golf, Golf Plus").assertIsDisplayed()
    }

    @Test
    fun `familie_fara_modele_afiseaza_avertismentul_si_dezactiveaza_Next`() {
        var nextClicked = false
        setContent(
            uiState = CreateChallengeUiState(
                form = CreateChallengeFormState(selectedBrand = family.brand, selectedFamilyId = family.id),
                familiesState = FamiliesState.Content(listOf(family)),
                modelsState = ModelsState.EmptyForFamily,
            ),
            onAction = { if (it == CreateChallengeAction.NextStep) nextClicked = true },
        )

        composeTestRule.onNodeWithText("This family has no car models yet. Posts can't be matched to it.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").assertIsNotEnabled()

        composeTestRule.onNodeWithText("Next").performClick()
        assertEquals(false, nextClicked)
    }

    @Test
    fun `eroare_la_incarcarea_modelelor_afiseaza_mesajul_si_Retry_declanseaza_RetryLoadModels`() {
        var retried = false
        setContent(
            uiState = CreateChallengeUiState(
                form = CreateChallengeFormState(selectedBrand = family.brand, selectedFamilyId = family.id),
                familiesState = FamiliesState.Content(listOf(family)),
                modelsState = ModelsState.Error("Server error"),
            ),
            onAction = { if (it == CreateChallengeAction.RetryLoadModels) retried = true },
        )

        composeTestRule.onNodeWithText("Couldn't load models.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `vehicul_valid_activeaza_Next_si_click_declanseaza_NextStep`() {
        var nextClicked = false
        setContent(
            uiState = CreateChallengeUiState(
                form = CreateChallengeFormState(selectedBrand = family.brand, selectedFamilyId = family.id),
                familiesState = FamiliesState.Content(listOf(family)),
                modelsState = ModelsState.Content(models),
            ),
            onAction = { if (it == CreateChallengeAction.NextStep) nextClicked = true },
        )

        composeTestRule.onNodeWithText("Next").assertIsEnabled()
        composeTestRule.onNodeWithText("Next").performClick()
        assertEquals(true, nextClicked)
    }
}
