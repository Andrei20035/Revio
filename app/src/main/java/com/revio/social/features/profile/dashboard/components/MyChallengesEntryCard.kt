package com.revio.social.features.profile.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.components.shimmer
import com.revio.social.features.challenge.components.ChallengeAccent
import com.revio.social.features.challenge.label
import com.revio.social.features.challenge.components.SEGMENT_COUNT_THRESHOLD
import com.revio.social.features.challenge.components.ContinuousProgressBar
import com.revio.social.features.challenge.components.SegmentedProgress
import com.revio.social.features.profile.dashboard.MyChallengesEntryUiState

private val CardCornerRadius = 12.dp
private val CardBorderColor = Color(0xFF545454)
private val CardFill = Color(0xFF1C1C1C)
private val CardMinHeight = 72.dp
private val CardPadding = 14.dp
private val TextMuted = Color(0xFF707070)

/**
 * The permanent "My Challenges" entry point on the caller's own Profile — always visible,
 * adaptive to three content variants, and never showing its own error (that's [MyChallengesEntryUiState]'s
 * job: a failed refresh just keeps the last-known content). See the plan's §3 wireframe.
 */
@Composable
fun MyChallengesEntryCard(
    state: MyChallengesEntryUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = when (state) {
        MyChallengesEntryUiState.Loading -> "My Challenges"
        MyChallengesEntryUiState.Empty -> "My Challenges. No challenges yet."
        is MyChallengesEntryUiState.Active ->
            "My Challenges. ${state.titleLine}. ${state.contributionCount} of ${state.requiredPosts} spotted."
        is MyChallengesEntryUiState.Summary ->
            "My Challenges. ${state.completedCount} completed of ${state.joinedCount}."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CardMinHeight)
            .clip(RoundedCornerShape(CardCornerRadius))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Open My Challenges",
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = description }
            .border(1.dp, CardBorderColor, RoundedCornerShape(CardCornerRadius))
            .background(CardFill)
            .padding(CardPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MY CHALLENGES",
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        when (state) {
            MyChallengesEntryUiState.Loading -> LoadingLines()
            MyChallengesEntryUiState.Empty -> ContentLines(
                primary = "No challenges yet",
                secondary = "Join the next one to earn points",
            )

            is MyChallengesEntryUiState.Active -> ActiveContent(state)

            is MyChallengesEntryUiState.Summary -> ContentLines(
                primary = "${state.completedCount} completed of ${state.joinedCount}",
                secondary = "${state.pointsEarned} pts from challenges",
            )
        }
    }
}

@Composable
private fun ActiveContent(state: MyChallengesEntryUiState.Active) {
    val filledCount = minOf(state.contributionCount, state.requiredPosts)
    val timeLabel = state.remaining.label()

    Text(
        text = state.titleLine,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    Spacer(modifier = Modifier.height(6.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(56.dp)) {
            if (state.requiredPosts > SEGMENT_COUNT_THRESHOLD) {
                ContinuousProgressBar(
                    filledFraction = filledCount.toFloat() / state.requiredPosts.toFloat(),
                    accent = ChallengeAccent,
                )
            } else {
                SegmentedProgress(
                    filledCount = filledCount,
                    totalCount = state.requiredPosts,
                    accent = ChallengeAccent,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        val caption = if (timeLabel != null) {
            "$filledCount of ${state.requiredPosts} · ${timeLabel.removeSuffix(" remaining")}"
        } else {
            "$filledCount of ${state.requiredPosts}"
        }
        Text(
            text = caption,
            color = TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ContentLines(primary: String, secondary: String) {
    Text(
        text = primary,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = secondary,
        color = TextMuted,
        fontSize = 12.sp,
    )
}

@Composable
private fun LoadingLines() {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(16.dp)
            .shimmer(RoundedCornerShape(4.dp)),
    )
    Spacer(modifier = Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(14.dp)
            .shimmer(RoundedCornerShape(4.dp)),
    )
}
