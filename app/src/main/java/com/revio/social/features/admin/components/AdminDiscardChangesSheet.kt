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

private val SheetSurface = Color(0xFF11162E)
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.65f)
private val TextTertiary = Color.White.copy(alpha = 0.35f)
private val DangerRed = Color(0xFFFF5A5F)
private val LeaveAccent = Color(0xFF34D7C4)

/**
 * Single-step "leave the create-challenge wizard" confirmation. Copy and button color depend on
 * [hasSavedDraft]: with no draft yet, leaving loses everything ("Discard", red — genuinely
 * destructive, same convention as [AdminCancelChallengeSheet]); once a draft has been saved, the
 * admin is only leaving it unpublished, not losing it ("Leave", teal) — see the plan's §5.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDiscardChangesSheet(
    hasSavedDraft: Boolean,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onKeepEditing,
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
                text = if (hasSavedDraft) "Leave without publishing?" else "Discard this challenge?",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasSavedDraft) {
                    "Your draft is saved. It just isn't published yet."
                } else {
                    "Your changes won't be saved."
                },
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onKeepEditing) {
                    Text("Keep editing", color = TextSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onDiscard,
                    colors = ButtonDefaults.buttonColors(containerColor = if (hasSavedDraft) LeaveAccent else DangerRed),
                ) {
                    Text(
                        text = if (hasSavedDraft) "Leave" else "Discard",
                        color = if (hasSavedDraft) Color.Black else Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
