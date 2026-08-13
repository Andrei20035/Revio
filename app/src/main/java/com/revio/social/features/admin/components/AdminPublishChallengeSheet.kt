package com.revio.social.features.admin.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.data.model.AdminChallenge

private val SheetSurface = Color(0xFF11162E)
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.65f)
private val TextTertiary = Color.White.copy(alpha = 0.35f)
private val AccentYellow = Color(0xFFF0AB25)
private val DangerAccent = Color(0xFFFF5A5F)

/**
 * Single-step publish confirmation: a read-only summary of the DRAFT challenge about to go live,
 * plus a Publish button. Stateless w.r.t. the network — [onConfirm] fires the actual
 * `POST /admin/challenges/{id}/publish` call; the caller owns [isSubmitting] and [errorMessage]
 * (a 409 window-overlap conflict surfaces here as inline text). Pattern mirrors
 * `AdminRemovePostSheet`, collapsed to one step since there's nothing to pick — just review and
 * confirm (plan §5.5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPublishChallengeSheet(
    challenge: AdminChallenge,
    isSubmitting: Boolean,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
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
                text = "Publish challenge",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Review the details below. Publishing starts the challenge's scheduled window.",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))

            SummaryRow(label = "Family", value = challenge.targetFamilyId.toString())
            SummaryRow(label = "Required posts", value = challenge.requiredPosts.toString())
            SummaryRow(label = "Reward", value = "${challenge.rewardPoints} pts")
            SummaryRow(label = "Window", value = "${challenge.startsAt} → ${challenge.endsAt}")
            SummaryRow(label = "Timezone", value = challenge.adminTimezone)

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = errorMessage, color = DangerAccent, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text("Cancel", color = TextSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentYellow),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp, color = Color.Black)
                    } else {
                        Text("Publish", color = Color.Black, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
