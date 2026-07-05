package com.example.carspotter.features.activity.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.carspotter.R
import com.example.carspotter.core.ui.scaling.actScaled
import com.example.carspotter.core.ui.scaling.actScaledText
import com.example.carspotter.core.util.toRelativeTime
import com.example.carspotter.features.activity.model.ActivityItem

private val CardFill = Color(0x524E4E4E)
private val CardBorder = Color(0xFF363636)
private val CardShape = RoundedCornerShape(12.dp)
private val TimestampColor = Color(0xFF9D9D9D)

@Composable
fun CommentActivityCard(item: ActivityItem.CommentItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 97.dp.actScaled())
            .clip(CardShape)
            .border(1.dp, CardBorder, CardShape)
            .background(CardFill)
            .padding(horizontal = 20.dp.actScaled(), vertical = 12.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.actorAvatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.profile_picture),
            fallback = painterResource(R.drawable.profile_picture),
            error = painterResource(R.drawable.profile_picture),
            modifier = Modifier
                .size(37.dp.actScaled())
                .clip(CircleShape),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp.actScaled()),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(item.actorUsername)
                    }
                    append(" commented: \"${item.commentText}\"")
                },
                color = Color.White,
                fontSize = 14.sp.actScaledText(),
                maxLines = 3,
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

        AsyncImage(
            model = item.postThumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(36.dp.actScaled())
                .height(45.dp.actScaled())
                .clip(RoundedCornerShape(2.dp)),
        )
    }
}
