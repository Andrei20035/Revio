package com.revio.app.features.activity.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.app.core.ui.scaling.actScaled
import com.revio.app.core.ui.scaling.actScaledText
import com.revio.app.core.util.toRelativeTime
import com.revio.app.features.activity.model.ActivityItem

private val CardFill = Color(0x524E4E4E)
private val LeaderboardGreen = Color(0xFF28AB00)
private val CardShape = RoundedCornerShape(12.dp)
private val TimestampColor = Color(0xFF9D9D9D)

@Composable
fun LeaderboardUpCard(item: ActivityItem.LeaderboardUpItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(75.dp.actScaled())
            .clip(CardShape)
            .border(1.dp, LeaderboardGreen, CardShape)
            .background(CardFill)
            .padding(horizontal = 20.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = LeaderboardGreen,
            modifier = Modifier.size(32.dp.actScaled()),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp.actScaled()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "You moved up ${item.placesMoved} places in the leaderboard!",
                color = Color.White,
                fontSize = 14.sp.actScaledText(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.createdAt.toRelativeTime(),
                color = TimestampColor,
                fontSize = 13.3.sp.actScaledText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp.actScaled()),
        )
    }
}
