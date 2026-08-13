package com.revio.social.features.admin.challenge.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.overlay.OverlayAccent
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.theme.Poppins
import java.time.ZoneId

private val CardFill = Color(0x524E4E4E)
private val CardBorder = Color(0xFF363636)
private val TextSecondary = Color.White.copy(alpha = 0.65f)
private val ActionColor = Color(0xFF34D7C4)
private val ErrorColor = Color(0xFFF93939)
private val MaxModelsShownInSummary = 4

/** Exact copy `CreateChallengeViewModel.mapUpdateConflictError` remaps a 409 "not editable"
 * response to — the challenge was published (or otherwise left DRAFT) by someone else between
 * load and submit. Recognized here to swap the usual CTAs for a single "Open details": retrying
 * the same submit can't succeed, since the underlying assumption (still DRAFT) no longer holds. */
private const val LIFECYCLE_CONFLICT_MESSAGE = "This challenge was already published."

/** Step 3 — schedule the window (or start now), then review every field before saving/publishing.
 * `Save draft`/`Publish challenge` dispatch [CreateChallengeAction.SaveDraft]/
 * [CreateChallengeAction.RequestPublish] ("Save changes" in edit mode); the VM validates and
 * guards against double-submit. After a create-succeeded-but-publish-failed
 * [SubmitState.PartialSuccess], the same two buttons relabel to `Keep as draft`/`Retry publish`
 * ([CreateChallengeAction.KeepAsDraft]/`.RetryPublish`) — the confirmation sheet itself lives in
 * `CreateChallengeScreen.kt`. A [LIFECYCLE_CONFLICT_MESSAGE] [SubmitState.Failed] instead shows a
 * single "Open details", reusing `KeepAsDraft`'s existing "just navigate, no more network calls"
 * behavior — the being-edited challenge's id is already [CreateChallengeUiState.createdDraftId]. */
@Composable
fun ScheduleReviewStep(uiState: CreateChallengeUiState, onAction: (CreateChallengeAction) -> Unit) {
    val form = uiState.form

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Schedule & review",
            color = Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(20.dp.actScaled()))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Start now",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp.actScaledText(),
            )
            Switch(
                checked = form.startNow,
                onCheckedChange = { onAction(CreateChallengeAction.SetStartNow(it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ActionColor,
                    checkedTrackColor = ActionColor.copy(alpha = 0.5f),
                ),
                modifier = Modifier.testTag("schedule_start_now_switch"),
            )
        }
        Spacer(modifier = Modifier.height(16.dp.actScaled()))

        if (!form.startNow) {
            DateTimeField(
                label = "Starts",
                value = form.startsAtLocal,
                onValueChanged = { onAction(CreateChallengeAction.UpdateStartsAt(it)) },
                isError = uiState.fieldErrors[CreateChallengeField.SCHEDULE] != null,
            )
            Spacer(modifier = Modifier.height(16.dp.actScaled()))
        }

        DateTimeField(
            label = "Ends",
            value = form.endsAtLocal,
            onValueChanged = { onAction(CreateChallengeAction.UpdateEndsAt(it)) },
            isError = uiState.fieldErrors[CreateChallengeField.SCHEDULE] != null,
        )
        uiState.fieldErrors[CreateChallengeField.SCHEDULE]?.let { message ->
            Spacer(modifier = Modifier.height(4.dp.actScaled()))
            FieldWarning(message)
        }
        Spacer(modifier = Modifier.height(24.dp.actScaled()))

        ReviewCard(uiState = uiState)
        Spacer(modifier = Modifier.height(24.dp.actScaled()))

        val submitState = uiState.submitState
        when (submitState) {
            is SubmitState.Failed -> {
                Text(text = submitState.message, color = ErrorColor, fontSize = 13.sp.actScaledText())
                Spacer(modifier = Modifier.height(12.dp.actScaled()))
            }
            is SubmitState.PartialSuccess -> {
                Text(
                    text = "Draft saved, but publishing failed.",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp.actScaledText(),
                )
                Spacer(modifier = Modifier.height(2.dp.actScaled()))
                Text(text = submitState.publishErrorMessage, color = ErrorColor, fontSize = 13.sp.actScaledText())
                Spacer(modifier = Modifier.height(12.dp.actScaled()))
            }
            SubmitState.Idle, SubmitState.Submitting -> Unit
        }

        val isSubmitting = submitState == SubmitState.Submitting
        when {
            submitState is SubmitState.Failed && submitState.message == LIFECYCLE_CONFLICT_MESSAGE -> {
                PublishButton(
                    label = "Open details",
                    onClick = { onAction(CreateChallengeAction.KeepAsDraft) },
                )
            }

            submitState is SubmitState.PartialSuccess -> {
                SaveDraftButton(
                    label = "Keep as draft",
                    enabled = !isSubmitting,
                    onClick = { onAction(CreateChallengeAction.KeepAsDraft) },
                )
                Spacer(modifier = Modifier.height(12.dp.actScaled()))
                PublishButton(
                    label = "Retry publish",
                    enabled = !isSubmitting,
                    onClick = { onAction(CreateChallengeAction.RetryPublish) },
                )
            }

            else -> {
                SaveDraftButton(
                    label = if (uiState.mode is CreateChallengeMode.Edit) "Save changes" else "Save draft",
                    enabled = !isSubmitting,
                    onClick = { onAction(CreateChallengeAction.SaveDraft) },
                )
                Spacer(modifier = Modifier.height(12.dp.actScaled()))
                PublishButton(
                    enabled = !isSubmitting,
                    onClick = { onAction(CreateChallengeAction.RequestPublish) },
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(uiState: CreateChallengeUiState) {
    val form = uiState.form
    val familyName = (uiState.familiesState as? FamiliesState.Content)
        ?.families
        ?.firstOrNull { it.id == form.selectedFamilyId }
        ?.name

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .background(CardFill)
            .padding(16.dp.actScaled()),
    ) {
        SummaryRow(label = "Title", value = form.title.ifBlank { "—" })
        SummaryRow(label = "Family", value = familyDisplay(form.selectedBrand, familyName))
        SummaryRow(label = "Included models", value = modelsSummary(uiState.modelsState))
        SummaryRow(label = "Required posts", value = form.requiredPostsInput.ifBlank { "—" })
        SummaryRow(label = "Reward", value = rewardDisplay(form.rewardPointsInput))
        SummaryRow(label = "Starts", value = startsDisplay(form))
        SummaryRow(label = "Ends", value = form.endsAtLocal?.toDisplayString() ?: "—")
        SummaryRow(label = "Timezone", value = ZoneId.systemDefault().id)
    }
}

private fun familyDisplay(brand: String?, familyName: String?): String = when {
    brand != null && familyName != null -> "$brand · $familyName"
    brand != null -> brand
    else -> "—"
}

private fun modelsSummary(modelsState: ModelsState): String {
    val models = (modelsState as? ModelsState.Content)?.models ?: return "—"
    if (models.isEmpty()) return "—"
    val shown = models.take(MaxModelsShownInSummary)
    val remaining = models.size - shown.size
    val names = shown.joinToString(", ") { it.model }
    return if (remaining > 0) "$names +$remaining more" else names
}

private fun rewardDisplay(rewardPointsInput: String): String =
    rewardPointsInput.ifBlank { null }?.let { "$it pts" } ?: "—"

private fun startsDisplay(form: CreateChallengeFormState): String = when {
    form.startNow -> "Now"
    form.startsAtLocal != null -> form.startsAtLocal.toDisplayString()
    else -> "—"
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp.actScaled()),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp.actScaledText())
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp.actScaledText(),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SaveDraftButton(label: String = "Save draft", enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (enabled) ActionColor else ActionColor.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, color, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp.actScaled()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp.actScaledText(),
        )
    }
}

@Composable
private fun PublishButton(label: String = "Publish challenge", enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) ActionColor else ActionColor.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp.actScaled()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.Black,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp.actScaledText(),
        )
    }
}
