package com.revio.social.core.ui.earlyspotter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.earlyspotter.EarlySpotterCardState
import com.revio.social.core.ui.overlay.OverlayAccent
import com.revio.social.core.ui.overlay.OverlayBorder
import com.revio.social.core.ui.overlay.OverlaySurface
import com.revio.social.core.ui.theme.Poppins

// Same palette as FirstPostFeedbackCard/TourOverlay, so the app's "needs your attention" cards match.
private val TextPrimary = Color.White
private val TextSecondary = Color(0xB3FFFFFF) // white @ 70%
private val CardCornerRadius = 20.dp

/**
 * One-time Early Spotter welcome/bonus card, presented modally over a scrim (see
 * [EarlySpotterHost]) the same way [com.revio.social.core.ui.feedback.FirstPostFeedbackCard]
 * presents its own. Renders nothing for [EarlySpotterCardState.Hidden] — callers still decide
 * whether to compose this at all.
 */
@Composable
fun EarlySpotterCard(
    state: EarlySpotterCardState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == EarlySpotterCardState.Hidden) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(CardCornerRadius))
            .clip(RoundedCornerShape(CardCornerRadius))
            .background(OverlaySurface)
            .border(1.dp, OverlayBorder, RoundedCornerShape(CardCornerRadius))
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = TextSecondary,
                )
            }
        }

        when (state) {
            is EarlySpotterCardState.Welcome -> WelcomeContent(earlySpotterNumber = state.earlySpotterNumber)
            is EarlySpotterCardState.Bonus -> BonusContent(points = state.points)
            EarlySpotterCardState.Hidden -> Unit
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = OverlayAccent),
        ) {
            Text("Got it", fontFamily = Poppins, fontSize = 14.sp)
        }
    }
}

@Composable
private fun WelcomeContent(earlySpotterNumber: Int) {
    Text(
        text = "Congratulations, you're an Early Spotter!",
        color = TextPrimary,
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "You're Early Spotter #$earlySpotterNumber.",
        color = TextSecondary,
        fontFamily = Poppins,
        fontSize = 14.sp,
    )
}

@Composable
private fun BonusContent(points: Int) {
    Text(
        text = "You received $points points!",
        color = TextPrimary,
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Your Early Spotter bonus has been added to your score.",
        color = TextSecondary,
        fontFamily = Poppins,
        fontSize = 14.sp,
    )
}

// ---- Previews ----

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun EarlySpotterCardWelcomePreview() {
    EarlySpotterCard(state = EarlySpotterCardState.Welcome(earlySpotterNumber = 42), onDismiss = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun EarlySpotterCardBonusPreview() {
    EarlySpotterCard(state = EarlySpotterCardState.Bonus(points = 300), onDismiss = {})
}
