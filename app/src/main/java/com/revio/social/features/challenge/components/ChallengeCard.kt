package com.revio.social.features.challenge.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.revio.social.core.ui.scaling.scaled
import com.revio.social.core.ui.scaling.scaledText
import com.revio.social.data.model.RewardState
import com.revio.social.features.challenge.ChallengeUiState
import com.revio.social.features.challenge.RemainingTime
import com.revio.social.features.challenge.label

private val CtaTextColor = Color(0xFF00161F)

private val RefCardCornerRadius = 20.dp
private val RefCardPadding = 16.dp
private val RefBorderWidth = 1.dp
private val RefEyebrowFontSize = 11.sp
private val RefEyebrowLetterSpacing = 1.2.sp
private val RefTitleTopSpacing = 8.dp
private val RefTitleFontSize = 18.sp
private val RefSegmentsTopSpacing = 14.dp
private val RefPointsSpacing = 12.dp
private val RefPointsFontSize = 13.sp
private val RefCaptionTopSpacing = 8.dp
private val RefCaptionFontSize = 12.sp
private val RefCtaTopSpacing = 16.dp
private val RefCtaHeight = 48.dp
private val RefCtaCornerRadius = 14.dp
private val RefCtaFontSize = 15.sp

/**
 * Compact weekend-challenge card for the top of the Feed. Purely presentational: [state] must
 * already be [ChallengeUiState.Active] (the caller — [com.revio.social.features.challenge.ChallengeViewModel] —
 * decides visibility; this composable never hides itself). `isStale` is intentionally not read
 * here: a failed background refresh shows the same last-known-good data, no visual difference
 * (see the plan's §4).
 *
 * Two independent tap targets: the card body opens the (future) challenge detail screen via
 * [onCardClick], the CTA starts the camera flow via [onSpotNow]. The CTA is a nested `clickable`
 * inside the card's own `clickable` — Compose resolves a tap to whichever is innermost for that
 * screen region, so a CTA tap never also fires [onCardClick]. The card's rich description is set
 * as an explicit (non-merging) string built from [state] and applied with `mergeDescendants` to
 * only the informational column — deliberately *not* the whole card — so the CTA keeps its own,
 * independent accessible name instead of being absorbed into the card's merged node.
 */
@Composable
fun ChallengeCard(
    state: ChallengeUiState.Active,
    onCardClick: () -> Unit,
    onSpotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isGranted = state.rewardState == RewardState.GRANTED
    val filledCount = minOf(state.contributionCount, state.requiredPosts)
    val accent = if (isGranted) ChallengeGold else ChallengeAccent
    val timeLabel = state.remaining.label()
    val isUrgent = (state.remaining as? RemainingTime.HoursMinutes)?.let { it.hours < URGENT_THRESHOLD_HOURS } ?: false

    val captionText = if (isGranted) {
        "${state.rewardPoints} pts earned"
    } else {
        "$filledCount of ${state.requiredPosts} spotted"
    }
    val ctaLabel = if (isGranted) "Spot again" else "Spot now"

    val aggregateDescription = buildString {
        append("Challenge: ${state.titleLine}. ")
        append("$filledCount of ${state.requiredPosts} spotted. ")
        append("${state.rewardPoints} points.")
        if (isGranted) {
            append(" Reward earned.")
        } else if (timeLabel != null) {
            append(" $timeLabel.")
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RefCardCornerRadius.scaled()))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Open challenge details",
                onClick = onCardClick,
            )
            .semantics { contentDescription = "Open challenge details" }
            .border(RefBorderWidth, CardBorder, RoundedCornerShape(RefCardCornerRadius.scaled()))
            .background(CardBackground)
            .padding(RefCardPadding.scaled()),
    ) {
        Column {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = aggregateDescription
                },
            ) {
                val eyebrowSuffix = if (isGranted) "COMPLETE" else timeLabel.orEmpty()
                Text(
                    text = if (eyebrowSuffix.isEmpty()) "CHALLENGE" else "CHALLENGE · $eyebrowSuffix",
                    color = if (!isGranted && isUrgent) ChallengeGold else Color.White.copy(alpha = 0.64f),
                    fontSize = RefEyebrowFontSize.scaledText(),
                    letterSpacing = RefEyebrowLetterSpacing,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(RefTitleTopSpacing.scaled()))

                Text(
                    text = state.titleLine,
                    color = Color.White,
                    fontSize = RefTitleFontSize.scaledText(),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(RefSegmentsTopSpacing.scaled()))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (state.requiredPosts > SEGMENT_COUNT_THRESHOLD) {
                            ContinuousProgressBar(
                                filledFraction = filledCount.toFloat() / state.requiredPosts.toFloat(),
                                accent = accent,
                            )
                        } else {
                            SegmentedProgress(
                                filledCount = filledCount,
                                totalCount = state.requiredPosts,
                                accent = accent,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(RefPointsSpacing.scaled()))
                    Text(
                        text = "${state.rewardPoints} pts",
                        color = accent,
                        fontSize = RefPointsFontSize.scaledText(),
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(RefCaptionTopSpacing.scaled()))

                Text(
                    text = captionText,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = RefCaptionFontSize.scaledText(),
                )
            }

            Spacer(modifier = Modifier.height(RefCtaTopSpacing.scaled()))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RefCtaHeight.scaled())
                    .clip(RoundedCornerShape(RefCtaCornerRadius.scaled()))
                    .background(ChallengeAccent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "Start a camera spot",
                        onClick = onSpotNow,
                    )
                    .semantics(mergeDescendants = true) {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ctaLabel,
                    color = CtaTextColor,
                    fontSize = RefCtaFontSize.scaledText(),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
