package com.revio.social.features.challenge.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.data.model.ChallengeSummary

private val StripCornerRadius = 12.dp
private val StripHeight = 77.dp
private val StripBorderWidth = 1.dp
private val DividerHeight = 36.dp
private val LabelColor = Color(0xFF707070)

/**
 * "My Challenges"' summary band — deliberately mirrors [com.revio.social.features.profile.dashboard.ProfileDashboardScreen]'s
 * `StatsCard` (same fill/border/height/radius) for visual continuity between the two screens, per
 * the plan's §3/§6 wireframe.
 */
@Composable
fun ChallengeSummaryStrip(summary: ChallengeSummary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(StripBorderWidth, CardBorder, RoundedCornerShape(StripCornerRadius))
            .clip(RoundedCornerShape(StripCornerRadius))
            .background(CardBackground)
            .height(StripHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryItem(label = "JOINED", value = "${summary.joinedCount}")
        SummaryDivider()
        SummaryItem(label = "COMPLETED", value = "${summary.completedCount}")
        SummaryDivider()
        SummaryItem(label = "PTS EARNED", value = "${summary.pointsEarned}")
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    // mergeDescendants + an explicit "label value" order: unmerged, TalkBack would read the
    // digits before the label that gives them meaning ("3" then "Joined" instead of "Joined 3").
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value" },
    ) {
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(text = label, color = LabelColor, fontSize = 11.sp, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(DividerHeight)
            .background(Color.White.copy(alpha = 0.08f)),
    )
}
