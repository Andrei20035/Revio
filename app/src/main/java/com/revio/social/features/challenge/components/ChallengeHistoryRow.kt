package com.revio.social.features.challenge.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import com.revio.social.data.model.ChallengeHistoryItem
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.features.challenge.challengeHistoryStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RowLabelColor = Color(0xFF707070)
private val RowDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val RowCornerRadius = 12.dp
private val RowSegmentWidth = 44.dp

/**
 * One row of "My Challenges"' history list. Accent/eyebrow follow the plan's §3 state table:
 * gold only for a granted reward, [ChallengeAccent] for an active or reward-pending challenge,
 * [RowLabelColor] for anything else — an ended, not-completed challenge is never shown as an
 * error, just as a fact ("2 of 5", not a red "not completed").
 */
@Composable
fun ChallengeHistoryRow(
    item: ChallengeHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = challengeHistoryStatus(
        effectiveStatus = item.effectiveStatus,
        participantState = item.progress.participantState,
        rewardState = item.progress.rewardState,
        contributionCount = item.progress.contributionCount,
        requiredPosts = item.challenge.requiredPosts,
    )
    val isGranted = status.isGranted
    val isRevoked = status.isRevoked
    val isCompletedPending = status.isCompletedPending

    val accent = if (isGranted) {
        ChallengeGold
    } else if (isCompletedPending || item.effectiveStatus == EffectiveChallengeStatus.ACTIVE) {
        ChallengeAccent
    } else {
        RowLabelColor
    }

    val eyebrow = when {
        isGranted -> "COMPLETED"
        isRevoked -> "REWARD REVOKED"
        isCompletedPending -> "REWARD PENDING"
        item.effectiveStatus == EffectiveChallengeStatus.ACTIVE -> "ACTIVE"
        item.effectiveStatus == EffectiveChallengeStatus.SCHEDULED ->
            "STARTS ${item.challenge.startsAt.atZone(ZoneId.systemDefault()).format(RowDateFormatter)}"
        item.effectiveStatus == EffectiveChallengeStatus.ENDED -> "ENDED"
        item.effectiveStatus == EffectiveChallengeStatus.CANCELLED -> "CANCELLED"
        else -> "UNKNOWN"
    }

    val titleLine = "Spot ${item.challenge.requiredPosts} ${item.challenge.targetFamilyBrand} ${item.challenge.targetFamilyName}"
    val period = "${item.challenge.startsAt.atZone(ZoneId.systemDefault()).format(RowDateFormatter)} – " +
        item.challenge.endsAt.atZone(ZoneId.systemDefault()).format(RowDateFormatter)
    val progressCaption = if (isGranted) {
        "+${item.challenge.rewardPoints} pts"
    } else {
        "${item.progress.contributionCount} of ${item.challenge.requiredPosts}"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(RowCornerRadius))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Open challenge details",
                onClick = onClick,
            )
            // mergeDescendants: without it, TalkBack would announce this contentDescription and
            // then separately re-announce every child Text — merging collapses the row into one
            // accessible node, matching ChallengeCard's convention.
            .semantics(mergeDescendants = true) { contentDescription = "$titleLine. $eyebrow. $period. $progressCaption." }
            .background(CardBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(RowSegmentWidth)) {
            SegmentedProgress(
                filledCount = minOf(item.progress.contributionCount, item.challenge.requiredPosts),
                totalCount = item.challenge.requiredPosts,
                accent = accent,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleLine,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$period · $progressCaption",
                color = RowLabelColor,
                fontSize = 12.sp,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = eyebrow,
            color = accent,
            fontSize = 11.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.Medium,
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = RowLabelColor,
        )
    }
}
