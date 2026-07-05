package com.example.carspotter.features.activity.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carspotter.R
import com.example.carspotter.core.ui.scaling.actScaled
import com.example.carspotter.core.ui.scaling.actScaledText
import com.example.carspotter.core.util.toRelativeTime
import com.example.carspotter.features.activity.model.ActivityItem

private val CardFill = Color(0x524E4E4E)
private val StreakOrange = Color(0xFFFF6641)
private val CardShape = RoundedCornerShape(12.dp)
private val TimestampColor = Color(0xFF9D9D9D)

@Composable
fun StreakCard(item: ActivityItem.StreakItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(75.dp.actScaled())
            .clip(CardShape)
            .border(1.dp, StreakOrange, CardShape)
            .background(CardFill)
            .padding(horizontal = 20.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.fire),
            contentDescription = null,
            modifier = Modifier
                .width(20.dp.actScaled())
                .height(24.dp.actScaled()),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp.actScaled()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "You reached a ${item.streakDays} day streak.",
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
    }
}
