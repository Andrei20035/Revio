package com.revio.social.features.admin.challenge.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.theme.Poppins

private val NextButtonFill = Color(0xFF34D7C4)
private val HintColor = Color(0xFF8D8D8D)

/** Step 2 — title, optional description, required posts, reward points. `Next` stays enabled
 * (unlike Vehicle's) so tapping it is what surfaces [CreateChallengeField] errors — see the
 * plan's §5: "Next rămâne activ în pasul 2, ca să declanșeze afișarea erorilor." */
@Composable
fun GoalRewardStep(uiState: CreateChallengeUiState, onAction: (CreateChallengeAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "What does it take?",
            color = Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(20.dp.actScaled()))

        WizardTextField(
            label = "Title",
            value = uiState.form.title,
            onValueChange = { onAction(CreateChallengeAction.UpdateTitle(it)) },
            placeholder = "e.g. Weekend Golf Hunt",
            imeAction = ImeAction.Next,
            maxLength = 150,
            isError = uiState.fieldErrors[CreateChallengeField.TITLE] != null,
        )
        uiState.fieldErrors[CreateChallengeField.TITLE]?.let { FieldWarning(it) }
        Spacer(modifier = Modifier.height(16.dp.actScaled()))

        WizardTextField(
            label = "Description (optional)",
            value = uiState.form.description,
            onValueChange = { onAction(CreateChallengeAction.UpdateDescription(it)) },
            placeholder = "What should admins know about this challenge?",
            imeAction = ImeAction.Default,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
        )
        Spacer(modifier = Modifier.height(20.dp.actScaled()))

        WizardTextField(
            label = "Required posts",
            value = uiState.form.requiredPostsInput,
            onValueChange = { onAction(CreateChallengeAction.UpdateRequiredPosts(it)) },
            placeholder = "5",
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
            isError = uiState.fieldErrors[CreateChallengeField.REQUIRED_POSTS] != null,
        )
        FieldHint("Qualifying posts each user must publish.")
        uiState.fieldErrors[CreateChallengeField.REQUIRED_POSTS]?.let { FieldWarning(it) }
        Spacer(modifier = Modifier.height(16.dp.actScaled()))

        WizardTextField(
            label = "Reward points",
            value = uiState.form.rewardPointsInput,
            onValueChange = { onAction(CreateChallengeAction.UpdateRewardPoints(it)) },
            placeholder = "300",
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            isError = uiState.fieldErrors[CreateChallengeField.REWARD_POINTS] != null,
        )
        FieldHint("Points granted when they finish.")
        uiState.fieldErrors[CreateChallengeField.REWARD_POINTS]?.let { FieldWarning(it) }

        Spacer(modifier = Modifier.height(28.dp.actScaled()))
        NextButton(onClick = { onAction(CreateChallengeAction.NextStep) })
    }
}

@Composable
private fun FieldHint(text: String) {
    Text(
        text = text,
        color = HintColor,
        fontSize = 12.sp.actScaledText(),
        modifier = Modifier.padding(top = 4.dp.actScaled(), start = 4.dp.actScaled()),
    )
}

@Composable
private fun NextButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NextButtonFill)
            .clickable(onClick = onClick)
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
