package com.revio.app.features.leaderboard.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.app.R
import com.revio.app.core.ui.theme.Poppins
import com.revio.app.features.leaderboard.LeaderboardEntry
import java.text.NumberFormat
import java.util.Locale

private val RowShape       = RoundedCornerShape(12.dp)
private val RowBackground  = Color(0xFF1C1C1C)
private val RowBorder      = Color(0xFF5D5D5D)
private val StreakColor    = Color(0xFFFF6641)

@Composable
fun LeaderboardUserRow(
    entry: LeaderboardEntry,
    modifier: Modifier = Modifier,
    onAvatarClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(55.dp)
            .clip(RowShape)
            .border(1.dp, RowBorder, RowShape)
            .background(RowBackground)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Rank "5." ─────────────────────────────────────────────────────
        // Fixed width handles double-digit ranks without layout shifts.
        Text(
            text = "${entry.rank}.",
            color = Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
        )

        // ── Avatar ────────────────────────────────────────────────────────
        LeaderboardAvatar(
            url = entry.avatarUrl,
            size = 37.dp,
            username = entry.username,
            onClick = onAvatarClick,
        )

        Spacer(Modifier.width(12.dp))

        // ── Username (fills remaining horizontal space) ───────────────────
        Text(
            text = entry.username,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // ── Right block: SpotScore + streak ───────────────────────────────
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = formatScore(entry.spotScore),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
            if (entry.streakDays > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.fire),
                        contentDescription = null,
                        modifier = Modifier
                            .width(10.dp)
                            .height(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${entry.streakDays} ${if (entry.streakDays == 1) "Day" else "Days"}",
                        color = StreakColor,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

private fun formatScore(value: Int): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value)
