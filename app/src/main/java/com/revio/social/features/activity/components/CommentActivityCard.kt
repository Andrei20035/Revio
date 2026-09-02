package com.revio.social.features.activity.components

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
import com.revio.social.R
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.util.toRelativeTime
import com.revio.social.features.activity.model.ActivityItem

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
                    val spot = "${item.brand.orEmpty()} ${item.model.orEmpty()} spot"
                    // Mirrors NotificationEventService.renderCommentCopy's 1 / 2-4 / 5+ thresholds
                    // (plan Partea II, Pasul 5). Only the un-aggregated case (actorCount <= 1)
                    // shows the actual comment text — ActivityService only keeps it for that case.
                    when {
                        item.actorCount <= 1 -> {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(item.actorUsername)
                            }
                            append(" commented: \"${item.commentText}\"")
                        }
                        item.actorCount <= 4 -> {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(item.actorUsername)
                            }
                            val others = item.actorCount - 1
                            val othersWord = if (others == 1) "other" else "others"
                            append(" and $others $othersWord joined the conversation on your $spot")
                        }
                        else -> append("${item.actorCount} people commented on your $spot")
                    }
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
            error = painterResource(R.drawable.post_placeholder),
            fallback = painterResource(R.drawable.post_placeholder),
            modifier = Modifier
                .width(36.dp.actScaled())
                .height(45.dp.actScaled())
                .clip(RoundedCornerShape(2.dp)),
        )
    }
}
