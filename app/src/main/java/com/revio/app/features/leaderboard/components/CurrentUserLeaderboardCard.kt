package com.revio.app.features.leaderboard.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.app.R
import com.revio.app.core.ui.theme.Poppins
import com.revio.app.features.leaderboard.CurrentUserStanding
import java.text.NumberFormat
import java.util.Locale

private val CardBackground  = Color(0xFF1C1C1C)
private val CardBorder      = Color(0xFF5D5D5D)
private val TextMuted       = Color(0xFF9D9D9D)
private val StreakTextColor = Color(0xFFFF6641)
private val CardShape       = RoundedCornerShape(12.dp)

@Composable
fun CurrentUserLeaderboardCard(
    standing: CurrentUserStanding,
    modifier: Modifier = Modifier,
    onAvatarClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(63.dp)
            .clip(CardShape)
            .border(1.dp, CardBorder, CardShape)
            .background(CardBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Left: rank number + movement indicator ────────────────────────
        Column(
            modifier = Modifier.width(88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "#${standing.entry.rank}",
                color = Color.White,
                fontFamily = Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 22.sp,
            )
            RankMovementIndicator(
                movement = standing.movement,
                placesMoved = standing.placesMoved,
            )
        }

        // ── Vertical divider ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(47.dp)
                .background(Color(0x33FFFFFF)),
        )

        Spacer(Modifier.width(12.dp))

        // ── Avatar ────────────────────────────────────────────────────────
        LeaderboardAvatar(
            url = standing.entry.avatarUrl,
            size = 37.dp,
            username = standing.entry.username,
            onClick = onAvatarClick,
        )

        Spacer(Modifier.width(10.dp))

        Column(
        ) {
            // ── "You" label ───────────────────────────────────────────────────
            Text(
                text = "You",
                color = TextMuted,
                fontFamily = Poppins,
                fontSize = 13.sp,
            )

            // ── Streak (centred, fills remaining space) ───────────────────────
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.fire),
                    contentDescription = null,
                    modifier = Modifier
                        .width(10.dp)
                        .height(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "${standing.entry.streakDays} ${if (standing.entry.streakDays == 1) "Day" else "Days"}",
                    color = StreakTextColor,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── SpotScore ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(end = 16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "SpotScore",
                color = TextMuted,
                fontSize = 13.sp,
                fontFamily = Poppins
            )
            Text(
                text = formatScore(standing.entry.spotScore),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
        }
    }
}

private fun formatScore(value: Int): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value)
