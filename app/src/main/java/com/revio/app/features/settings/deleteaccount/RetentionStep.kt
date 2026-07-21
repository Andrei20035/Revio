package com.revio.app.features.settings.deleteaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.app.core.ui.scaling.actScaled
import com.revio.app.core.ui.scaling.actScaledText
import com.revio.app.core.ui.theme.ProfileAccentGold

private val CardBg = Color(0x3DD9D9D9)

@Composable
fun RetentionStep(
    uiState: DeleteAccountUiState,
    onAction: (DeleteAccountAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val stats = uiState.stats
        if (stats != null) {
            Text(
                text = "Before you go, here's what you've built:",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp.actScaledText(),
            )

            Spacer(modifier = Modifier.height(20.dp.actScaled()))

            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(value = stats.postCount.toString(), label = "Spots posted", modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp.actScaled()))
                StatTile(value = stats.likesReceived.toString(), label = "Likes received", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp.actScaled()))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    value = stats.leaderboardRank?.let { "#$it" } ?: "—",
                    label = "Leaderboard rank",
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp.actScaled()))
                StatTile(value = stats.streakDays.toString(), label = "Day streak", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(28.dp.actScaled()))
        }

        if (uiState.selectedReason == DeletionReason.TOO_MANY_NOTIFICATIONS) {
            Text(
                text = "Sorry about that — too many notifications can be overwhelming.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp.actScaledText(),
            )
            Spacer(modifier = Modifier.height(20.dp.actScaled()))
        }

        Text(
            text = "Are you sure you want to leave the Revio community? " +
                "You won't be able to see amazing carspots around the world anymore.",
            color = Color.White,
            fontSize = 16.sp.actScaledText(),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(32.dp.actScaled()))

        Button(
            onClick = { onAction(DeleteAccountAction.NextStep) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp.actScaled()),
            shape = RoundedCornerShape(33.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProfileAccentGold),
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

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(CardBg, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp.actScaled(), horizontal = 12.dp.actScaled()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = ProfileAccentGold,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(4.dp.actScaled()))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp.actScaledText(),
            textAlign = TextAlign.Center,
        )
    }
}
