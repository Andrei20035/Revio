package com.revio.social.features.admin.challenge.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.revio.social.core.navigation.Screen
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.features.admin.AppScreenBackgroundWithTopBar
import com.revio.social.features.admin.challenge.ADMIN_CHALLENGE_CHANGED_KEY
import com.revio.social.features.admin.components.AdminDiscardChangesSheet

private val SheetSurface = Color(0xFF11162E)
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.65f)
private val TextTertiary = Color.White.copy(alpha = 0.35f)
private val PublishAccent = Color(0xFF34D7C4)

/** The create-challenge wizard's single navigation destination — back/discard handling, the
 * publish confirmation sheet, and the navigate-to-detail effect (finalized here per the plan's
 * Etapa 8; the three step composables and the VM's save/publish orchestration were built earlier). */
@Composable
fun CreateChallengeScreen(
    navController: NavController,
    viewModel: CreateChallengeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val onBack: () -> Unit = {
        if (uiState.step == WizardStep.Vehicle) {
            viewModel.onAction(CreateChallengeAction.RequestClose)
        } else {
            viewModel.onAction(CreateChallengeAction.PreviousStep)
        }
    }

    BackHandler(enabled = uiState.step != WizardStep.Vehicle || uiState.isDirty) {
        onBack()
    }

    LaunchedEffect(uiState.navigateToDetailChallengeId) {
        val challengeId = uiState.navigateToDetailChallengeId ?: return@LaunchedEffect
        navController.getBackStackEntry(Screen.AdminChallenges.route)
            .savedStateHandle[ADMIN_CHALLENGE_CHANGED_KEY] = true
        navController.navigate(Screen.AdminChallengeDetail.createRoute(challengeId)) {
            popUpTo(Screen.AdminChallengeCreate.route) { inclusive = true }
        }
        viewModel.consumeNavigateToDetail()
    }

    LaunchedEffect(uiState.closeRequested) {
        if (uiState.closeRequested) {
            navController.popBackStack()
            viewModel.consumeClose()
        }
    }

    AppScreenBackgroundWithTopBar(
        title = "New challenge",
        onBack = onBack,
    ) {
        CreateChallengeContent(uiState = uiState, onAction = viewModel::onAction)
    }

    if (uiState.showPublishSheet) {
        PublishConfirmationSheet(
            onConfirm = { viewModel.onAction(CreateChallengeAction.ConfirmPublish) },
            onDismiss = { viewModel.onAction(CreateChallengeAction.DismissPublishSheet) },
        )
    }

    if (uiState.showDiscardSheet) {
        AdminDiscardChangesSheet(
            hasSavedDraft = uiState.createdDraftId != null,
            onKeepEditing = { viewModel.onAction(CreateChallengeAction.DismissDiscardSheet) },
            onDiscard = { viewModel.onAction(CreateChallengeAction.ConfirmDiscard) },
        )
    }
}

@Composable
fun CreateChallengeContent(
    uiState: CreateChallengeUiState,
    onAction: (CreateChallengeAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(top = 12.dp.actScaled()),
    ) {
        WizardStepIndicator(step = uiState.step, modifier = Modifier.testTag("create_challenge_step_indicator"))
        Spacer(modifier = Modifier.height(20.dp.actScaled()))

        when (uiState.step) {
            WizardStep.Vehicle -> VehicleStep(uiState = uiState, onAction = onAction)
            WizardStep.GoalReward -> GoalRewardStep(uiState = uiState, onAction = onAction)
            WizardStep.ScheduleReview -> ScheduleReviewStep(uiState = uiState, onAction = onAction)
        }

        Spacer(modifier = Modifier.height(32.dp.actScaled()))
    }
}

/** Final confirmation before create+publish actually fires — the review card on the Schedule step
 * already shows every field, so this is a fixed-copy "are you sure", not another data summary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishConfirmationSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextTertiary) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Publish this challenge?",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "It goes live from the start time and users can begin earning points. " +
                    "You can't unpublish it.",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = PublishAccent),
                ) {
                    Text("Publish", color = Color.Black, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
