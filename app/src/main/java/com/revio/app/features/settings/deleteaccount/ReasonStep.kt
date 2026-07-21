package com.revio.app.features.settings.deleteaccount

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.app.core.ui.scaling.actScaled
import com.revio.app.core.ui.scaling.actScaledText
import com.revio.app.core.ui.theme.ProfileAccentGold
import com.revio.app.features.profile.components.LabeledTextField

private val reasonLabels: Map<DeletionReason, String> = mapOf(
    DeletionReason.TOO_MANY_NOTIFICATIONS to "Too many notifications",
    DeletionReason.NOT_INTERESTING_CARSPOTS to "I don't find interesting carspots",
    DeletionReason.FOUND_BETTER_APP to "I found a better app",
    DeletionReason.PRIVACY_CONCERNS to "Privacy concerns",
    DeletionReason.TAKING_A_BREAK to "I'm taking a break",
    DeletionReason.OTHER to "Other",
)

@Composable
fun ReasonStep(
    uiState: DeleteAccountUiState,
    onAction: (DeleteAccountAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Why are you leaving?",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp.actScaledText(),
        )

        Spacer(modifier = Modifier.height(20.dp.actScaled()))

        reasonLabels.forEach { (reason, label) ->
            val selected = uiState.selectedReason == reason
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onAction(DeleteAccountAction.SelectReason(reason)) },
                    )
                    .padding(vertical = 10.dp.actScaled()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onAction(DeleteAccountAction.SelectReason(reason)) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = ProfileAccentGold,
                        unselectedColor = Color.White.copy(alpha = 0.5f),
                    ),
                )
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 16.sp.actScaledText(),
                )
            }
        }

        if (uiState.selectedReason == DeletionReason.OTHER) {
            Spacer(modifier = Modifier.height(8.dp.actScaled()))
            LabeledTextField(
                label = "Tell us more (optional)",
                value = uiState.otherText,
                onValueChange = { onAction(DeleteAccountAction.OtherTextChanged(it)) },
                placeholderText = "What went wrong?",
            )
        }

        Spacer(modifier = Modifier.height(32.dp.actScaled()))

        Button(
            onClick = { onAction(DeleteAccountAction.NextStep) },
            enabled = uiState.selectedReason != null,
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

        Spacer(modifier = Modifier.height(24.dp.actScaled()))
    }
}
