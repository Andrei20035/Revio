package com.revio.social.core.ui.feedback

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.overlay.OverlayAccent
import com.revio.social.core.ui.overlay.OverlayBorder
import com.revio.social.core.ui.overlay.OverlaySurface
import com.revio.social.core.ui.theme.Poppins
import com.revio.social.data.model.QuickReason

// Card palette — shared with TourOverlay so the two "app needs your attention" moments match.
private val TextPrimary = Color.White
private val TextSecondary = Color(0xB3FFFFFF) // white @ 70%
private val TextTertiary = Color(0x80FFFFFF)   // white @ 50%
private val ErrorColor = Color(0xFFFF5A5F)

private val CardCornerRadius = 20.dp
private val EmojiFontSize = 28.sp

/** One step of the first-post feedback card's rating → reason → comment flow. */
sealed interface FirstPostFeedbackStep {
    data object Rating : FirstPostFeedbackStep
    data class Reason(val rating: Int) : FirstPostFeedbackStep
    data class Comment(val rating: Int, val reason: QuickReason) : FirstPostFeedbackStep
}

/** Everything [FirstPostFeedbackCard] needs to render — fully hoisted, no internal state. */
data class FirstPostFeedbackCardState(
    val step: FirstPostFeedbackStep,
    val comment: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

private data class ReasonOption(val label: String, val reason: QuickReason)

private fun reasonPromptFor(rating: Int): String = when {
    rating <= 2 -> "What made posting difficult?"
    rating == 3 -> "What could we make clearer?"
    else -> "What did you like most?"
}

private fun reasonOptionsFor(rating: Int): List<ReasonOption> = when {
    rating <= 2 -> listOf(
        ReasonOption("Upload was difficult", QuickReason.UPLOAD_DIFFICULT),
        ReasonOption("Location was confusing", QuickReason.LOCATION_CONFUSING),
        ReasonOption("Car details were confusing", QuickReason.CAR_DETAILS_CONFUSING),
        ReasonOption("Took too long", QuickReason.TOOK_TOO_LONG),
        ReasonOption("Something didn't work", QuickReason.SOMETHING_BROKE),
        ReasonOption("Other", QuickReason.OTHER),
    )
    rating == 3 -> listOf(
        ReasonOption("Upload process", QuickReason.UPLOAD_PROCESS),
        ReasonOption("Location", QuickReason.LOCATION),
        ReasonOption("Car details", QuickReason.CAR_DETAILS),
        ReasonOption("Description", QuickReason.DESCRIPTION),
        ReasonOption("Posting confirmation", QuickReason.POSTING_CONFIRMATION),
        ReasonOption("Other", QuickReason.OTHER),
    )
    else -> listOf(
        ReasonOption("Easy to use", QuickReason.EASY_TO_USE),
        ReasonOption("Fast", QuickReason.FAST),
        ReasonOption("Clear", QuickReason.CLEAR),
        ReasonOption("Fun", QuickReason.FUN),
        ReasonOption("Looks good", QuickReason.LOOKS_GOOD),
        ReasonOption("Other", QuickReason.OTHER),
    )
}

private val RatingEmojis = listOf("😞", "😕", "😐", "🙂", "🤩")

/**
 * Compact first-post feedback card, presented modally over a scrim (see
 * [com.revio.social.core.ui.feedback.FirstPostFeedbackHost]) the same way
 * [com.revio.social.core.ui.tour.TourOverlay] presents its coach-mark panel. Callers are
 * responsible for positioning it (e.g.
 * `Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) { FirstPostFeedbackCard(...) }`)
 * and for deciding whether to compose it at all — this composable only knows how to render the
 * rating → reason → comment steps, nothing about eligibility, timing, or persistence.
 */
@Composable
fun FirstPostFeedbackCard(
    state: FirstPostFeedbackCardState,
    onRatingSelected: (Int) -> Unit,
    onReasonSelected: (QuickReason) -> Unit,
    onCommentChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSkip: () -> Unit,
    onNotNow: () -> Unit,
    onCloseX: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(CardCornerRadius))
            .clip(RoundedCornerShape(CardCornerRadius))
            .background(OverlaySurface)
            .border(1.dp, OverlayBorder, RoundedCornerShape(CardCornerRadius))
            .animateContentSize()
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onCloseX, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss feedback prompt",
                    tint = TextTertiary,
                )
            }
        }

        when (val step = state.step) {
            is FirstPostFeedbackStep.Rating -> RatingStep(onRatingSelected = onRatingSelected, onNotNow = onNotNow)
            is FirstPostFeedbackStep.Reason -> ReasonStep(rating = step.rating, onReasonSelected = onReasonSelected)
            is FirstPostFeedbackStep.Comment -> CommentStep(
                comment = state.comment,
                isSubmitting = state.isSubmitting,
                errorMessage = state.errorMessage,
                onCommentChanged = onCommentChanged,
                onSend = onSend,
                onSkip = onSkip,
            )
        }
    }
}

@Composable
private fun RatingStep(
    onRatingSelected: (Int) -> Unit,
    onNotNow: () -> Unit,
) {
    Text(
        text = "How did posting your first spot feel?",
        color = TextPrimary,
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RatingEmojis.forEachIndexed { index, emoji ->
            val rating = index + 1
            Text(
                text = emoji,
                fontSize = EmojiFontSize,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onRatingSelected(rating) },
                ),
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Not good", color = TextTertiary, fontFamily = Poppins, fontSize = 11.sp)
        Text("Okay", color = TextTertiary, fontFamily = Poppins, fontSize = 11.sp)
        Text("Great", color = TextTertiary, fontFamily = Poppins, fontSize = 11.sp)
    }
    Spacer(modifier = Modifier.height(12.dp))
    TextButton(onClick = onNotNow) {
        Text("Not now", color = TextSecondary, fontFamily = Poppins, fontSize = 13.sp)
    }
}

@Composable
private fun ReasonStep(
    rating: Int,
    onReasonSelected: (QuickReason) -> Unit,
) {
    Text(
        text = reasonPromptFor(rating),
        color = TextPrimary,
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    )
    Spacer(modifier = Modifier.height(14.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        reasonOptionsFor(rating).forEach { option ->
            ReasonChip(label = option.label, onClick = { onReasonSelected(option.reason) })
        }
    }
}

@Composable
private fun ReasonChip(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x1AFFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = label, color = TextPrimary, fontFamily = Poppins, fontSize = 13.sp)
    }
}

@Composable
private fun CommentStep(
    comment: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onCommentChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSkip: () -> Unit,
) {
    Text(
        text = "Thanks! Anything you'd like to add?",
        color = TextPrimary,
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    )
    Spacer(modifier = Modifier.height(14.dp))
    OutlinedTextField(
        value = comment,
        onValueChange = onCommentChanged,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSubmitting,
        placeholder = { Text("Tell us more… (optional)", color = TextTertiary) },
        maxLines = 4,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = OverlayAccent,
            focusedBorderColor = OverlayAccent,
            unfocusedBorderColor = OverlayBorder,
        ),
    )
    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = errorMessage, color = ErrorColor, fontFamily = Poppins, fontSize = 12.sp)
    }
    Spacer(modifier = Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onSkip, enabled = !isSubmitting) {
            Text("Skip", color = TextSecondary, fontFamily = Poppins, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onSend,
            enabled = !isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = OverlayAccent),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = OverlaySurface,
                )
            } else {
                Text("Send feedback", fontFamily = Poppins, fontSize = 14.sp)
            }
        }
    }
}

// ---- Previews ----

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun FirstPostFeedbackCardRatingPreview() {
    FirstPostFeedbackCard(
        state = FirstPostFeedbackCardState(step = FirstPostFeedbackStep.Rating),
        onRatingSelected = {},
        onReasonSelected = {},
        onCommentChanged = {},
        onSend = {},
        onSkip = {},
        onNotNow = {},
        onCloseX = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun FirstPostFeedbackCardReasonPreview() {
    FirstPostFeedbackCard(
        state = FirstPostFeedbackCardState(step = FirstPostFeedbackStep.Reason(rating = 2)),
        onRatingSelected = {},
        onReasonSelected = {},
        onCommentChanged = {},
        onSend = {},
        onSkip = {},
        onNotNow = {},
        onCloseX = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun FirstPostFeedbackCardCommentPreview() {
    FirstPostFeedbackCard(
        state = FirstPostFeedbackCardState(
            step = FirstPostFeedbackStep.Comment(rating = 5, reason = QuickReason.EASY_TO_USE),
            comment = "Loved how quick it was!",
        ),
        onRatingSelected = {},
        onReasonSelected = {},
        onCommentChanged = {},
        onSend = {},
        onSkip = {},
        onNotNow = {},
        onCloseX = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun FirstPostFeedbackCardErrorPreview() {
    FirstPostFeedbackCard(
        state = FirstPostFeedbackCardState(
            step = FirstPostFeedbackStep.Comment(rating = 1, reason = QuickReason.SOMETHING_BROKE),
            comment = "",
            errorMessage = "Couldn't send feedback. Try again.",
        ),
        onRatingSelected = {},
        onReasonSelected = {},
        onCommentChanged = {},
        onSend = {},
        onSkip = {},
        onNotNow = {},
        onCloseX = {},
    )
}
