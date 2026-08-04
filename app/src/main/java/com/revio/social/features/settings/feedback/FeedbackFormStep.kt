package com.revio.social.features.settings.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.overlay.OverlayAccent
import com.revio.social.core.ui.overlay.OverlayBorder
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.theme.ProfileAccentGold
import com.revio.social.data.model.ConfusionReason
import com.revio.social.data.model.FeedbackArea
import com.revio.social.data.model.FeedbackCategory
import com.revio.social.data.model.FeedbackPriority

private val LabelColor = Color.White
private val HintColor = Color(0xFF8D8D8D)

private val ratingEmojis = listOf("😞", "😕", "😐", "🙂", "🤩")

private val confusionReasonLabels: Map<ConfusionReason, String> = mapOf(
    ConfusionReason.DIDNT_KNOW_WHAT_TO_DO_NEXT to "I didn't know what to do next",
    ConfusionReason.WORDING_NOT_CLEAR to "The wording wasn't clear",
    ConfusionReason.COULDNT_FIND_SOMETHING to "I couldn't find something",
    ConfusionReason.UNEXPECTED_RESULT to "The result was unexpected",
    ConfusionReason.TOO_MUCH_INFORMATION to "There was too much information",
    ConfusionReason.OTHER to "Other",
)

private val priorityLabels: Map<FeedbackPriority, String> = mapOf(
    FeedbackPriority.NICE_TO_HAVE to "Nice to have",
    FeedbackPriority.IMPORTANT to "Important",
    FeedbackPriority.BLOCKING to "Blocking me",
)

private val notWorkingAreas = listOf(
    FeedbackArea.POSTING, FeedbackArea.FEED, FeedbackArea.PROFILE, FeedbackArea.ACTIVITY,
    FeedbackArea.LEADERBOARD, FeedbackArea.SETTINGS, FeedbackArea.AUTHENTICATION, FeedbackArea.OTHER,
)

private val confusingAreas = listOf(
    FeedbackArea.POSTING, FeedbackArea.FEED, FeedbackArea.PROFILE, FeedbackArea.ACTIVITY,
    FeedbackArea.LEADERBOARD, FeedbackArea.SETTINGS, FeedbackArea.NAVIGATION, FeedbackArea.OTHER,
)

private val featureIdeaAreas = listOf(
    FeedbackArea.POSTING, FeedbackArea.FEED, FeedbackArea.PROFILE, FeedbackArea.ACTIVITY,
    FeedbackArea.LEADERBOARD, FeedbackArea.SETTINGS, FeedbackArea.NEW_AREA, FeedbackArea.NOT_SURE,
)

private fun areaLabel(area: FeedbackArea): String = when (area) {
    FeedbackArea.POSTING -> "Posting"
    FeedbackArea.FEED -> "Feed"
    FeedbackArea.PROFILE -> "Profile"
    FeedbackArea.ACTIVITY -> "Activity"
    FeedbackArea.LEADERBOARD -> "Leaderboard"
    FeedbackArea.SETTINGS -> "Settings"
    FeedbackArea.AUTHENTICATION -> "Authentication"
    FeedbackArea.NAVIGATION -> "Navigation"
    FeedbackArea.NEW_AREA -> "New area"
    FeedbackArea.NOT_SURE -> "Not sure"
    FeedbackArea.OTHER -> "Other"
}

@Composable
fun FeedbackFormStep(
    uiState: FeedbackUiState,
    onAction: (FeedbackAction) -> Unit,
) {
    val category = uiState.category ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        when (category) {
            FeedbackCategory.NOT_WORKING -> NotWorkingForm(uiState, onAction)
            FeedbackCategory.CONFUSING -> ConfusingForm(uiState, onAction)
            FeedbackCategory.FEATURE_IDEA -> FeatureIdeaForm(uiState, onAction)
            FeedbackCategory.GENERAL -> GeneralForm(uiState, onAction)
        }

        Spacer(modifier = Modifier.height(24.dp.actScaled()))

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = Color(0xFFF93939),
                fontSize = 13.sp.actScaledText(),
            )
            Spacer(modifier = Modifier.height(12.dp.actScaled()))
        }

        Button(
            onClick = { onAction(FeedbackAction.NextStep) },
            enabled = uiState.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp.actScaled()),
            shape = RoundedCornerShape(33.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ProfileAccentGold,
                disabledContainerColor = ProfileAccentGold.copy(alpha = 0.4f),
            ),
        ) {
            Text(
                text = "Continue",
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp.actScaledText(),
            )
        }
    }
}

@Composable
private fun NotWorkingForm(uiState: FeedbackUiState, onAction: (FeedbackAction) -> Unit) {
    FormMessageField(
        label = "What happened?",
        placeholder = "Describe the problem you encountered…",
        value = uiState.message,
        onValueChange = { onAction(FeedbackAction.MessageChanged(it)) },
    )
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    FormMessageField(
        label = "What were you trying to do? (optional)",
        placeholder = "For example: I was trying to publish a new spot…",
        value = uiState.secondaryMessage,
        onValueChange = { onAction(FeedbackAction.SecondaryMessageChanged(it)) },
        minLines = 2,
    )
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    AreaSection(
        label = "Affected area (optional)",
        areas = notWorkingAreas,
        selected = uiState.area,
        onSelected = { onAction(FeedbackAction.AreaSelected(it)) },
    )
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    DiagnosticsToggle(uiState = uiState, onAction = onAction)
}

@Composable
private fun ConfusingForm(uiState: FeedbackUiState, onAction: (FeedbackAction) -> Unit) {
    FormMessageField(
        label = "What was confusing?",
        placeholder = "Tell us what you expected to happen and what wasn't clear…",
        value = uiState.message,
        onValueChange = { onAction(FeedbackAction.MessageChanged(it)) },
    )
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    AreaSection(
        label = "Affected area (optional)",
        areas = confusingAreas,
        selected = uiState.area,
        onSelected = { onAction(FeedbackAction.AreaSelected(it)) },
    )
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    Text(
        text = "What made it confusing? (optional)",
        color = LabelColor,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp.actScaledText(),
    )
    Spacer(modifier = Modifier.height(10.dp.actScaled()))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp.actScaled()),
        verticalArrangement = Arrangement.spacedBy(8.dp.actScaled()),
    ) {
        confusionReasonLabels.forEach { (reason, label) ->
            SelectableChip(
                label = label,
                selected = uiState.quickReason == reason,
                onClick = {
                    onAction(FeedbackAction.QuickReasonSelected(if (uiState.quickReason == reason) null else reason))
                },
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    FormMessageField(
        label = "What did you expect to happen? (optional)",
        placeholder = "Tell us what you expected…",
        value = uiState.secondaryMessage,
        onValueChange = { onAction(FeedbackAction.SecondaryMessageChanged(it)) },
        minLines = 2,
    )
}

@Composable
private fun FeatureIdeaForm(uiState: FeedbackUiState, onAction: (FeedbackAction) -> Unit) {
    FormMessageField(
        label = "What would you like Revio to do?",
        placeholder = "Describe the feature or improvement you'd like to see…",
        value = uiState.message,
        onValueChange = { onAction(FeedbackAction.MessageChanged(it)) },
    )
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    FormMessageField(
        label = "Why would this be useful to you? (optional)",
        placeholder = "Tell us what problem it would solve…",
        value = uiState.secondaryMessage,
        onValueChange = { onAction(FeedbackAction.SecondaryMessageChanged(it)) },
        minLines = 2,
    )
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    Text(
        text = "Priority (optional)",
        color = LabelColor,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp.actScaledText(),
    )
    Spacer(modifier = Modifier.height(10.dp.actScaled()))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp.actScaled()),
        verticalArrangement = Arrangement.spacedBy(8.dp.actScaled()),
    ) {
        priorityLabels.forEach { (priority, label) ->
            SelectableChip(
                label = label,
                selected = uiState.priority == priority,
                onClick = {
                    onAction(FeedbackAction.PrioritySelected(if (uiState.priority == priority) null else priority))
                },
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    AreaSection(
        label = "Related area (optional)",
        areas = featureIdeaAreas,
        selected = uiState.area,
        onSelected = { onAction(FeedbackAction.AreaSelected(it)) },
    )
}

@Composable
private fun GeneralForm(uiState: FeedbackUiState, onAction: (FeedbackAction) -> Unit) {
    Text(
        text = "How are you feeling about Revio?",
        color = LabelColor,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp.actScaledText(),
    )
    Spacer(modifier = Modifier.height(10.dp.actScaled()))
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ratingEmojis.forEachIndexed { index, emoji ->
            val rating = index + 1
            val selected = uiState.rating == rating
            Text(
                text = emoji,
                fontSize = if (selected) 32.sp.actScaledText() else 26.sp.actScaledText(),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onAction(FeedbackAction.RatingSelected(rating)) },
                ),
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    FormMessageField(
        label = "What would you like us to know?",
        placeholder = "Share your thoughts about Revio…",
        value = uiState.message,
        onValueChange = { onAction(FeedbackAction.MessageChanged(it)) },
    )
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    FormMessageField(
        label = "What should we keep? (optional)",
        placeholder = "What's working well…",
        value = uiState.keepMessage,
        onValueChange = { onAction(FeedbackAction.KeepMessageChanged(it)) },
        minLines = 2,
    )
    Spacer(modifier = Modifier.height(20.dp.actScaled()))
    FormMessageField(
        label = "What should we improve? (optional)",
        placeholder = "What could be better…",
        value = uiState.improveMessage,
        onValueChange = { onAction(FeedbackAction.ImproveMessageChanged(it)) },
        minLines = 2,
    )
}

@Composable
private fun AreaSection(
    label: String,
    areas: List<FeedbackArea>,
    selected: FeedbackArea?,
    onSelected: (FeedbackArea?) -> Unit,
) {
    Text(
        text = label,
        color = LabelColor,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp.actScaledText(),
    )
    Spacer(modifier = Modifier.height(10.dp.actScaled()))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp.actScaled()),
        verticalArrangement = Arrangement.spacedBy(8.dp.actScaled()),
    ) {
        areas.forEach { area ->
            SelectableChip(
                label = areaLabel(area),
                selected = selected == area,
                onClick = { onSelected(if (selected == area) null else area) },
            )
        }
    }
}

@Composable
private fun DiagnosticsToggle(uiState: FeedbackUiState, onAction: (FeedbackAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Include app and device information",
                color = LabelColor,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp.actScaledText(),
            )
            Spacer(modifier = Modifier.height(4.dp.actScaled()))
            Text(
                text = "Includes app version, Android version, device model and basic " +
                    "diagnostic information. It does not include passwords or private messages.",
                color = HintColor,
                fontSize = 12.sp.actScaledText(),
            )
        }
        Spacer(modifier = Modifier.width(12.dp.actScaled()))
        Switch(
            checked = uiState.includeDiagnostics,
            onCheckedChange = { onAction(FeedbackAction.ToggleIncludeDiagnostics(it)) },
            colors = SwitchDefaults.colors(checkedThumbColor = ProfileAccentGold, checkedTrackColor = ProfileAccentGold.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun FormMessageField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 3,
) {
    Text(
        text = label,
        color = LabelColor,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp.actScaledText(),
    )
    Spacer(modifier = Modifier.height(10.dp.actScaled()))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(text = placeholder, color = HintColor) },
        minLines = minLines,
        maxLines = 6,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = OverlayAccent,
            focusedBorderColor = OverlayAccent,
            unfocusedBorderColor = OverlayBorder,
        ),
    )
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ProfileAccentGold else Color(0x1AFFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp.actScaled(), vertical = 8.dp.actScaled()),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp.actScaledText(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF030310)
@Composable
private fun FeedbackFormStepNotWorkingPreview() {
    FeedbackFormStep(
        uiState = FeedbackUiState(category = FeedbackCategory.NOT_WORKING, step = FeedbackStep.Form),
        onAction = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF030310)
@Composable
private fun FeedbackFormStepConfusingPreview() {
    FeedbackFormStep(
        uiState = FeedbackUiState(category = FeedbackCategory.CONFUSING, step = FeedbackStep.Form),
        onAction = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF030310)
@Composable
private fun FeedbackFormStepFeatureIdeaPreview() {
    FeedbackFormStep(
        uiState = FeedbackUiState(category = FeedbackCategory.FEATURE_IDEA, step = FeedbackStep.Form),
        onAction = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF030310)
@Composable
private fun FeedbackFormStepGeneralPreview() {
    FeedbackFormStep(
        uiState = FeedbackUiState(category = FeedbackCategory.GENERAL, step = FeedbackStep.Form, rating = 4),
        onAction = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF030310)
@Composable
private fun FeedbackFormStepErrorPreview() {
    FeedbackFormStep(
        uiState = FeedbackUiState(
            category = FeedbackCategory.NOT_WORKING,
            step = FeedbackStep.Form,
            message = "It crashed when I tried to post",
            errorMessage = "You're offline. Try again when you're connected.",
        ),
        onAction = {},
    )
}
