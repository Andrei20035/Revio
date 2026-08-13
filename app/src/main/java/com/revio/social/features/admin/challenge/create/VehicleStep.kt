package com.revio.social.features.admin.challenge.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.components.OfflineStateMessage
import com.revio.social.core.ui.components.RetryButton
import com.revio.social.core.ui.components.StateMessage
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.theme.Poppins
import java.util.UUID

private val NextButtonFill = Color(0xFF34D7C4)
private val CardFill = Color(0x524E4E4E)
private val CardBorder = Color(0xFF363636)
private val DangerText = Color(0xFFF93939)

/** Step 1 — brand → family → "included models" preview. Stateless: all data comes from
 * [uiState], every interaction goes back through [onAction]. */
@Composable
fun VehicleStep(uiState: CreateChallengeUiState, onAction: (CreateChallengeAction) -> Unit) {
    // Local, ephemeral — which overlay (if any) is open. Not form data, so it doesn't belong in
    // the VM's persisted state (consistent with the plan's §6 state-restoration decision).
    var brandOverlayVisible by rememberSaveable { mutableStateOf(false) }
    var familyOverlayVisible by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Which cars count?",
            color = Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(6.dp.actScaled()))
        Text(
            text = "Posts of any model in this family will count toward the challenge.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(20.dp.actScaled()))

        when (val familiesState = uiState.familiesState) {
            FamiliesState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp.actScaled()),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.testTag("create_challenge_loading"),
                )
            }

            is FamiliesState.Error -> if (familiesState.isOffline) {
                OfflineStateMessage(onRetry = { onAction(CreateChallengeAction.RetryLoadFamilies) })
            } else {
                StateMessage(
                    title = "Couldn't load car families",
                    subtitle = familiesState.message,
                    actionLabel = "Retry",
                    onAction = { onAction(CreateChallengeAction.RetryLoadFamilies) },
                )
            }

            FamiliesState.Empty -> StateMessage(
                title = "No car families yet",
                subtitle = "Create a family from the server admin tools first.",
            )

            is FamiliesState.Content -> {
                val brands = remember(familiesState.families) { brandsFromFamilies(familiesState.families) }
                val familiesForSelectedBrand = remember(familiesState.families, uiState.form.selectedBrand) {
                    uiState.form.selectedBrand
                        ?.let { familiesForBrand(familiesState.families, it) }
                        .orEmpty()
                }
                val selectedFamilyName = familiesForSelectedBrand
                    .firstOrNull { it.id == uiState.form.selectedFamilyId }
                    ?.name

                WizardSelectField(
                    label = "Brand",
                    selectedValue = uiState.form.selectedBrand,
                    placeholder = "Select a brand",
                    onClick = { brandOverlayVisible = true },
                )
                Spacer(modifier = Modifier.height(16.dp.actScaled()))
                WizardSelectField(
                    label = "Model family",
                    selectedValue = selectedFamilyName,
                    placeholder = "Select a family",
                    enabled = uiState.form.selectedBrand != null,
                    onClick = { familyOverlayVisible = true },
                )

                if (uiState.form.selectedFamilyId != null) {
                    Spacer(modifier = Modifier.height(20.dp.actScaled()))
                    ModelsPreview(modelsState = uiState.modelsState, onAction = onAction)
                }

                Spacer(modifier = Modifier.height(28.dp.actScaled()))
                NextButton(
                    enabled = validateVehicleStep(uiState),
                    onClick = { onAction(CreateChallengeAction.NextStep) },
                )

                WizardSelectOverlay(
                    visible = brandOverlayVisible,
                    items = brands.map { WizardSelectOption(id = it, label = it) },
                    onItemSelected = { brand ->
                        onAction(CreateChallengeAction.SelectBrand(brand))
                        brandOverlayVisible = false
                    },
                    onDismiss = { brandOverlayVisible = false },
                )
                WizardSelectOverlay(
                    visible = familyOverlayVisible,
                    items = familiesForSelectedBrand.map { WizardSelectOption(id = it.id.toString(), label = it.name) },
                    onItemSelected = { familyId ->
                        onAction(CreateChallengeAction.SelectFamily(UUID.fromString(familyId)))
                        familyOverlayVisible = false
                    },
                    onDismiss = { familyOverlayVisible = false },
                )
            }
        }
    }
}

@Composable
private fun ModelsPreview(modelsState: ModelsState, onAction: (CreateChallengeAction) -> Unit) {
    when (modelsState) {
        ModelsState.Idle -> Unit

        ModelsState.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp.actScaled()),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp.actScaled()),
                strokeWidth = 2.dp,
            )
        }

        is ModelsState.Content -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .background(CardFill)
                .padding(16.dp.actScaled())
                .testTag("create_challenge_models_preview"),
        ) {
            Text(
                text = "Included models (${modelsState.models.size})",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp.actScaledText(),
            )
            Spacer(modifier = Modifier.height(8.dp.actScaled()))
            Text(
                text = modelsState.models.joinToString(", ") { it.model },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp.actScaledText(),
            )
        }

        ModelsState.EmptyForFamily -> Text(
            text = "This family has no car models yet. Posts can't be matched to it.",
            color = DangerText,
            fontSize = 13.sp.actScaledText(),
            modifier = Modifier.testTag("create_challenge_models_preview"),
        )

        is ModelsState.Error -> Column {
            Text(
                text = "Couldn't load models.",
                color = DangerText,
                fontSize = 13.sp.actScaledText(),
            )
            Spacer(modifier = Modifier.height(6.dp.actScaled()))
            RetryButton(
                onClick = { onAction(CreateChallengeAction.RetryLoadModels) },
                spinning = false,
                label = "Retry",
            )
        }
    }
}

@Composable
private fun NextButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) NextButtonFill else NextButtonFill.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp.actScaled()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Next",
            color = Color.Black,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp.actScaledText(),
        )
    }
}
