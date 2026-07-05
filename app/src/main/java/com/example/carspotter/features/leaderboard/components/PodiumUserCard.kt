package com.example.carspotter.features.leaderboard.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carspotter.R
import com.example.carspotter.core.ui.theme.Poppins
import com.example.carspotter.features.leaderboard.LeaderboardEntry
import java.text.NumberFormat
import java.util.Locale

// ── Card dimensions ─────────────────────────────────────────────────────────
private val CardHeightCenter = 163.dp
private val CardHeightSide   = 145.dp
private val BadgeProtrusion  = 10.dp        // badge overflows this far above the card
private val CardTopPadding   = 22.dp        // space inside card before avatar
private val AvatarRingSize   = 55.dp
private val AvatarImageSize  = 37.dp
private val CardShape        = RoundedCornerShape(12.dp)
private val BadgeShape       = RoundedCornerShape(percent = 50)

// ── Podium badge colours ─────────────────────────────────────────────────────
private val Gold   = Color(0xFFFFD255)
private val Silver = Color(0xFFB5BAC6)
private val Bronze = Color(0xFFCB855C)

// ── Other colours ───────────────────────────────────────────────────────────
private val GlassBackground = Color(0x991C1C1C)
private val StreakTextColor = Color(0xFFFF6641)
private val BadgeTextColor  = Color(0xFF393939)

@Composable
fun PodiumUserCard(
    entry: LeaderboardEntry,
    modifier: Modifier = Modifier,
    onAvatarClick: () -> Unit = {},
) {
    val (badgeColor, cardHeight) = podiumStyle(entry.rank)
    // Total Box height = card height + badge that protrudes above the card.
    // Row uses Alignment.Bottom so the three outer Boxes bottom-align correctly.
    val boxHeight: Dp = cardHeight + BadgeProtrusion

    Box(modifier = modifier.height(boxHeight)) {

        // ── Card ─────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(cardHeight)
                .clip(CardShape)
                .border(1.dp, badgeColor, CardShape)
                .background(GlassBackground)
                .then(Modifier.height(cardHeight)),    // keep height explicit after clip
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Avatar: badge-coloured ring behind the photo
            Box(
                modifier = Modifier
                    .size(AvatarRingSize)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                badgeColor,
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center,
            ) {
                LeaderboardAvatar(
                    url = entry.avatarUrl,
                    size = AvatarImageSize,
                    username = entry.username,
                    onClick = onAvatarClick,
                )
            }

            AutoResizeUsernameText(
                text = entry.username,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = formatScore(entry.spotScore),
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(2.dp))

            // Streak row
            if (entry.streakDays > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
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
                        text = "${entry.streakDays} ${if (entry.streakDays == 1) "Day" else "Days"}",
                        color = StreakTextColor,
                        fontSize = 10.sp,
                        lineHeight = 10.sp
                    )
                }
            }
        }

        // ── Badge pill – floats above the card ───────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(38.dp)
                .height(19.dp)
                .clip(BadgeShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "#${entry.rank}",
                color = BadgeTextColor,
                fontFamily = Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 12.sp,
            )
        }
    }
}

/** Returns (borderColor/badgeColor, cardHeight) for a given podium rank. */
private fun podiumStyle(rank: Int): Pair<Color, Dp> = when (rank) {
    1    -> Gold   to CardHeightCenter
    2    -> Silver to CardHeightSide
    else -> Bronze to CardHeightSide
}

private fun formatScore(value: Int): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value)

@Composable
private fun AutoResizeUsernameText(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 13.sp,
    minFontSize: TextUnit = 9.sp,
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize,
        lineHeight = fontSize,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontSize > minFontSize) {
                fontSize = (fontSize.value - 0.5f).sp
            } else {
                readyToDraw = true
            }
        }
    )
}
