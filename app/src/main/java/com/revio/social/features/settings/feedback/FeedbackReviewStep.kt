package com.revio.social.features.settings.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.components.RetryButton
import com.revio.social.core.ui.overlay.OverlaySurface
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.theme.ProfileAccentGold
import com.revio.social.data.model.FeedbackCategory

private val LabelColor = Color(0xFF8D8D8D)
private val ValueColor = Color.White
private val ErrorColor = Color(0xFFF93939)

private fun categoryLabel(category: FeedbackCategory): String = when (category) {
    FeedbackCategory.NOT_WORKING -> "Something isn't working"
    FeedbackCategory.CONFUSING -> "Something is confusing"
    FeedbackCategory.FEATURE_IDEA -> "Feature idea"
    FeedbackCategory.GENERAL -> "General feedback"
}

@Composable
fun FeedbackReviewStep(
    uiState: FeedbackUiState,
    onAction: (FeedbackAction) -> Unit,
    onCancel: () -> Unit,
) {
    val category = uiState.category ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Review your feedback",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(20.dp.actScaled()))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(OverlaySurface)
                .padding(16.dp.actScaled()),
        ) {
            ReviewRow(label = "Category", value = categoryLabel(category))

            val message = uiState.message.ifBlank { null }
            if (message != null) {
                Spacer(modifier = Modifier.height(14.dp.actScaled()))
                ReviewRow(label = "Message", value = message)
            }

            Spacer(modifier = Modifier.height(14.dp.actScaled()))
            ReviewRow(
                label = "Technical information",
                value = if (uiState.includeDiagnostics) "Included" else "Not included",
            )
        }

        Spacer(modifier = Modifier.height(16.dp.actScaled()))

        Text(
            text = if (uiState.includeDiagnostics) {
                "App and device information helps us investigate problems faster."
            } else {
                "No app or device information will be sent with this feedback."
            },
            color = LabelColor,
            fontSize = 12.sp.actScaledText(),
        )

        Spacer(modifier = Modifier.height(24.dp.actScaled()))

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = ErrorColor,
                fontSize = 13.sp.actScaledText(),
            )
            Spacer(modifier = Modifier.height(12.dp.actScaled()))
        }

        Button(
            onClick = { onAction(FeedbackAction.Submit) },
            enabled = uiState.canSubmit && !uiState.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp.actScaled()),
            shape = RoundedCornerShape(33.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ProfileAccentGold,
                disabledContainerColor = ProfileAccentGold.copy(alpha = 0.4f),
            ),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp.actScaled()),
                    strokeWidth = 2.dp,
                    color = Color.Black,
                )
            } else {
                Text(
                    text = "Send feedback",
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp.actScaledText(),
                )
            }
        }

        if (uiState.errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp.actScaled()))
            RetryButton(onClick = { onAction(FeedbackAction.Retry) }, spinning = uiState.isSubmitting)
        }

        Spacer(modifier = Modifier.height(12.dp.actScaled()))

        TextButton(onClick = onCancel, enabled = !uiState.isSubmitting) {
            Text(text = "Cancel", color = LabelColor, fontSize = 14.sp.actScaledText())
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Column {
        Text(text = label, color = LabelColor, fontSize = 12.sp.actScaledText())
        Spacer(modifier = Modifier.height(4.dp.actScaled()))
        Text(text = value, color = ValueColor, fontSize = 14.sp.actScaledText())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF030310)
@Composable
private fun FeedbackReviewStepPreview() {
    FeedbackReviewStep(
        uiState = FeedbackUiState(
            category = FeedbackCategory.NOT_WORKING,
            step = FeedbackStep.Review,
            message = "It crashed when I tried to post",
        ),
        onAction = {},
        onCancel = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF030310)
@Composable
private fun FeedbackReviewStepErrorPreview() {
    FeedbackReviewStep(
        uiState = FeedbackUiState(
            category = FeedbackCategory.NOT_WORKING,
            step = FeedbackStep.Review,
            message = "It crashed when I tried to post",
            errorMessage = "You're offline. Try again when you're connected.",
            isOffline = true,
        ),
        onAction = {},
        onCancel = {},
    )
}
