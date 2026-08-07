package com.revio.social.features.challenge.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.revio.social.R
import com.revio.social.data.model.ChallengeContribution
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ThumbnailSize = 44.dp
private val ThumbnailRadius = 8.dp
private val CaptionColor = Color(0xFF707070)
private val ContributionTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)

/** One post that counted toward a challenge — the "YOUR SPOTS" list in Challenge Detail. */
@Composable
fun ChallengeContributionRow(contribution: ChallengeContribution, modifier: Modifier = Modifier) {
    val model = listOfNotNull(contribution.carBrand, contribution.carModel).joinToString(" ").ifBlank { "Spot" }
    val timestamp = contribution.createdAt.atZone(ZoneId.systemDefault()).format(ContributionTimestampFormatter)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBackground)
            // mergeDescendants: the thumbnail is decorative (contentDescription = null below) and
            // the two text lines describe the same spot — one combined node reads better than three.
            .semantics(mergeDescendants = true) { contentDescription = "$model, spotted $timestamp" }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(ThumbnailSize)
                .clip(RoundedCornerShape(ThumbnailRadius)),
        ) {
            AsyncImage(
                model = contribution.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(R.drawable.post_placeholder),
                error = painterResource(R.drawable.post_placeholder),
                fallback = painterResource(R.drawable.post_placeholder),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = model,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = timestamp,
                color = CaptionColor,
                fontSize = 12.sp,
            )
        }
    }
}
