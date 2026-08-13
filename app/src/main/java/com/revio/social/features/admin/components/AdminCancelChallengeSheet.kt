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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SheetSurface = Color(0xFF11162E)
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.65f)
private val TextTertiary = Color.White.copy(alpha = 0.35f)
private val DangerRed = Color(0xFFFF5A5F)

/**
 * Single-step cancel confirmation. Unlike revoke-all, cancel needs no second tap — the warning
 * copy itself is the confirmation barrier (plan §5.5). Stateless w.r.t. the network — [onConfirm]
 * fires the actual `POST /admin/challenges/{id}/cancel` call; the caller owns [isSubmitting].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCancelChallengeSheet(
    isSubmitting: Boolean,
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
                text = "Cancel this challenge?",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This will cancel the challenge and revoke any rewards already granted. " +
                    "This cannot be undone.",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text("Back", color = TextSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("Cancel challenge", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private enum class RevokeAllStep { WARNING, CONFIRM }

/**
 * Two-stage revoke-all confirmation, mirroring `AdminRemovePostSheet`'s REASON/CONFIRM shape:
 * a warning step, then an explicit second step whose own button is the only way to actually fire
 * the destructive action. The server independently requires the challenge id to be repeated in
 * the request body (`ChallengeAdminRoutes.kt:311-316`); this second tap is the UI's mirror of that
 * confirmation barrier, not a field to type (plan §5.5). Stateless w.r.t. the network —
 * [onConfirm] fires the actual `POST /admin/challenges/{id}/revoke-all` call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRevokeAllChallengeSheet(
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableStateOf(RevokeAllStep.WARNING) }

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = SheetSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextTertiary) },
    ) {
        when (step) {
            RevokeAllStep.WARNING -> RevokeAllWarningStep(
                onContinue = { step = RevokeAllStep.CONFIRM },
                onCancel = onDismiss,
            )

            RevokeAllStep.CONFIRM -> RevokeAllConfirmStep(
                isSubmitting = isSubmitting,
                onConfirm = onConfirm,
                onBack = { if (!isSubmitting) step = RevokeAllStep.WARNING },
            )
        }
    }
}

@Composable
private fun RevokeAllWarningStep(onContinue: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Revoke all rewards",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This retroactively revokes every reward already granted for this challenge, " +
                "regardless of whether it has ended. This cannot be undone.",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = TextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
            ) {
                Text("Continue", color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun RevokeAllConfirmStep(
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
            text = "Are you sure?",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap \"Revoke all\" to confirm. Every participant's granted reward for this " +
                "challenge will be revoked.",
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
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Revoke all", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
