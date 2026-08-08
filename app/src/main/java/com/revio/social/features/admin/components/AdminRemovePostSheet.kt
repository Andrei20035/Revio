package com.revio.social.features.admin.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.data.model.ModerationReason
import com.revio.social.data.model.label

private val SheetSurface = Color(0xFF11162E)
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.65f)
private val TextTertiary = Color.White.copy(alpha = 0.35f)
private val DangerAccent = Color(0xFFFF5A5F)
private val AccentYellow = Color(0xFFF0AB25)

private enum class RemovePostStep { REASON, CONFIRM }

/**
 * Admin-only "remove post" flow: pick one of the 13 moderation reasons (a free-text field is
 * required, non-blank, for OTHER), then confirm before the removal actually fires. Stateless
 * w.r.t. the network call — [onConfirm] carries the chosen reason (and, for OTHER, the details)
 * up to the caller, which owns the in-flight [isSubmitting] state and the actual API call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRemovePostSheet(
    isSubmitting: Boolean,
    onConfirm: (ModerationReason, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableStateOf(RemovePostStep.REASON) }
    var selectedReason by remember { mutableStateOf<ModerationReason?>(null) }
    var otherDetails by remember { mutableStateOf("") }

    val canContinue = selectedReason != null &&
        (selectedReason != ModerationReason.OTHER || otherDetails.isNotBlank())

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = SheetSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextTertiary) },
    ) {
        when (step) {
            RemovePostStep.REASON -> ReasonStep(
                selectedReason = selectedReason,
                otherDetails = otherDetails,
                onReasonSelected = { selectedReason = it },
                onOtherDetailsChanged = { otherDetails = it },
                canContinue = canContinue,
                onContinue = { step = RemovePostStep.CONFIRM },
                onCancel = onDismiss,
            )

            RemovePostStep.CONFIRM -> {
                val reason = selectedReason
                if (reason == null) {
                    step = RemovePostStep.REASON
                } else {
                    ConfirmStep(
                        reason = reason,
                        isSubmitting = isSubmitting,
                        onConfirm = {
                            onConfirm(reason, otherDetails.takeIf { reason == ModerationReason.OTHER })
                        },
                        onBack = { if (!isSubmitting) step = RemovePostStep.REASON },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasonStep(
    selectedReason: ModerationReason?,
    otherDetails: String,
    onReasonSelected: (ModerationReason) -> Unit,
    onOtherDetailsChanged: (String) -> Unit,
    canContinue: Boolean,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Remove post",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose why this post is being removed.",
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            ModerationReason.entries.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReasonSelected(reason) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedReason == reason,
                        onClick = { onReasonSelected(reason) },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentYellow, unselectedColor = TextSecondary),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = reason.label, color = TextPrimary, fontSize = 14.sp)
                }
            }

            if (selectedReason == ModerationReason.OTHER) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = otherDetails,
                    onValueChange = onOtherDetailsChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Describe the reason") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentYellow,
                    ),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SheetSurface)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = TextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onContinue,
                enabled = canContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentYellow,
                    disabledContainerColor = AccentYellow.copy(alpha = 0.35f),
                ),
            ) {
                Text("Continue", color = Color.Black, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ConfirmStep(
    reason: ModerationReason,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Confirm removal",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This post will be removed for: ${reason.label}. The author will be notified. This cannot be undone.",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onBack, enabled = !isSubmitting) {
                Text("Back", color = TextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = DangerAccent),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Remove post", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
