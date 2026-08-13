package com.revio.social.features.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.revio.social.core.ui.components.StateMessage
import com.revio.social.data.model.EffectiveChallengeStatus
import com.revio.social.data.model.RewardState
import com.revio.social.features.challenge.components.ChallengeAccent
import com.revio.social.features.challenge.components.ChallengeContributionRow
import com.revio.social.features.challenge.components.ChallengeGold
import com.revio.social.features.challenge.components.CardBackground
import com.revio.social.features.challenge.components.ContinuousProgressBar
import com.revio.social.features.challenge.components.SEGMENT_COUNT_THRESHOLD
import com.revio.social.features.challenge.components.SegmentedProgress
import com.revio.social.features.feed.components.rememberPostCreationLauncher
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ScreenBackground = Color(0xFF05081D)
private val TextMuted = Color(0xFF707070)
private val CtaTextColor = Color(0xFF00161F)
private val PeriodFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

/**
 * Weekend-challenge detail screen — reachable from the Feed card and (once it exists) My
 * Challenges. Purely presentational aside from wiring [ChallengeDetailViewModel]; see the plan's
 * §3/§6/§7 wireframe and flows.
 */
@Composable
fun ChallengeDetailScreen(
    navController: NavController,
    viewModel: ChallengeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openPostCreation = rememberPostCreationLauncher(navController)

    // Recomputes the countdown immediately (in case the app sat backgrounded past a minute
    // boundary, or past endsAt entirely) and kicks a coalesced refresh — mirrors FeedScreen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
            }

            when (val state = uiState) {
                ChallengeDetailUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = ChallengeAccent)
                }

                ChallengeDetailUiState.NotFound -> StateMessage(
                    title = "Challenge not found",
                    actionLabel = "Back",
                    onAction = { navController.popBackStack() },
                )

                is ChallengeDetailUiState.Error -> StateMessage(
                    title = "Couldn't load this challenge",
                    subtitle = state.message,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )

                is ChallengeDetailUiState.Content -> ChallengeDetailContent(
                    state = state,
                    onSpotNow = openPostCreation.openCamera,
                )
            }
        }
    }
}

@Composable
fun ChallengeDetailContent(
    state: ChallengeDetailUiState.Content,
    onSpotNow: () -> Unit,
) {
    val isGranted = state.rewardState == RewardState.GRANTED
    val isRevoked = !isGranted && state.rewardState == RewardState.NONE &&
        state.effectiveStatus == EffectiveChallengeStatus.ENDED && state.contributionCount >= state.requiredPosts
    // Reached the post threshold and it wasn't since taken away — a revoke already has its own
    // "Reward revoked" line in the REWARD card below, so it doesn't also get the checkmark badge.
    val isCompleted = !isRevoked && state.contributionCount >= state.requiredPosts
    val filledCount = minOf(state.contributionCount, state.requiredPosts)
    val accent = if (isGranted) ChallengeGold else ChallengeAccent
    val timeLabel = state.remaining.label()

    val eyebrowText = when {
        isGranted -> "COMPLETED"
        state.effectiveStatus == EffectiveChallengeStatus.SCHEDULED ->
            "STARTS ${state.startsAt.atZone(ZoneId.systemDefault()).format(PeriodFormatter)}"
        state.effectiveStatus == EffectiveChallengeStatus.ENDED -> "ENDED"
        timeLabel != null -> "CHALLENGE · $timeLabel"
        else -> "CHALLENGE"
    }
    val eyebrowColor = if (isGranted) ChallengeGold else if (state.effectiveStatus == EffectiveChallengeStatus.ACTIVE) {
        Color.White.copy(alpha = 0.64f)
    } else {
        TextMuted
    }

    val captionText = if (isGranted) {
        "${state.rewardPoints} pts earned"
    } else {
        "$filledCount of ${state.requiredPosts} spotted"
    }

    val rewardLine = when {
        isGranted -> "+${state.rewardPoints} pts earned"
        isRevoked -> "Reward revoked"
        state.effectiveStatus == EffectiveChallengeStatus.ACTIVE -> "${state.rewardPoints} pts if you finish"
        else -> "Not earned"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = eyebrowText,
            color = eyebrowColor,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.titleLine,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(6.dp))

        val period = "${state.startsAt.atZone(ZoneId.systemDefault()).format(PeriodFormatter)} – " +
            state.endsAt.atZone(ZoneId.systemDefault()).format(PeriodFormatter)
        Text(
            text = period,
            color = Color.White.copy(alpha = 0.64f),
            fontSize = 13.sp,
        )

        if (state.description != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.description,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

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
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${state.rewardPoints} pts",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = captionText,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
        )

        if (isCompleted) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Completed",
                    color = ChallengeGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "✓",
                    color = ChallengeGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isGranted) "+${state.rewardPoints} pts earned" else "Reward pending until challenge ends",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
            )
        }

        if (state.effectiveStatus == EffectiveChallengeStatus.ACTIVE) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
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
                    text = "Spot now",
                    color = CtaTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground)
                .padding(14.dp),
        ) {
            Text(
                text = "REWARD",
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = rewardLine,
                color = if (isGranted) ChallengeGold else if (isRevoked) TextMuted else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "YOUR SPOTS (${state.contributions.size})",
            color = Color.White.copy(alpha = 0.64f),
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (state.contributions.isEmpty()) {
            Text(
                text = "No spots yet — spots you post during this challenge will show up here.",
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 13.sp,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.contributions.forEach { contribution ->
                    ChallengeContributionRow(contribution)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
