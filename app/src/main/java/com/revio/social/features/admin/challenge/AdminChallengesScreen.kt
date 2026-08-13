package com.revio.social.features.admin.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.revio.social.core.ui.components.OfflineStateMessage
import com.revio.social.core.ui.components.StateMessage
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.util.toRelativeTime
import com.revio.social.data.model.AdminChallenge
import com.revio.social.data.model.ChallengeAdminStatus
import com.revio.social.features.admin.AppScreenBackgroundWithTopBar
import java.time.Instant
import kotlinx.coroutines.flow.distinctUntilChanged

private val CardFill = Color(0x524E4E4E)
private val CardBorder = Color(0xFF363636)
private val CardShape = RoundedCornerShape(12.dp)
private val SectionLabelColor = Color(0xFF707070)
private val CreateButtonFill = Color(0xFF34D7C4)

/** The admin "Challenges" dashboard (GET /api/admin/challenges) — previously had no UI at all. */
@Composable
fun AdminChallengesScreen(
    navController: NavController,
    viewModel: AdminChallengesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Requests the next page once the user scrolls near the end of the currently loaded list.
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount > 0 && lastVisibleIndex >= layoutInfo.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
    }

    AppScreenBackgroundWithTopBar(
        title = "Challenges",
        onBack = { navController.popBackStack() },
    ) {
        AdminChallengesContent(
            uiState = uiState,
            listState = listState,
            onRetry = viewModel::retry,
            // Wired once the create wizard exists (Bloc I) — no destination to navigate to yet.
            onCreateClick = {},
        )
    }
}

@Composable
fun AdminChallengesContent(
    uiState: AdminChallengesUiState,
    onRetry: () -> Unit,
    onCreateClick: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("admin_challenges_loading"),
            )
            uiState.isOffline -> OfflineStateMessage(
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
            uiState.errorMessage != null -> StateMessage(
                title = "Couldn't load challenges",
                subtitle = uiState.errorMessage,
                actionLabel = "Retry",
                onAction = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
            uiState.isEmpty -> StateMessage(
                title = "No challenges yet",
                subtitle = "Create the first one to get started.",
                modifier = Modifier.align(Alignment.Center),
            )
            else -> AdminChallengesList(
                uiState = uiState,
                listState = listState,
                modifier = Modifier.fillMaxSize(),
            )
        }

        CreateChallengeButton(
            onClick = onCreateClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp.actScaled()),
        )
    }
}

private enum class DashboardSection(val label: String) {
    ACTIVE("ACTIVE"),
    SCHEDULED("SCHEDULED"),
    DRAFTS("DRAFTS"),
    HISTORY("HISTORY"),
}

/** Buckets a challenge for display, deriving ACTIVE/HISTORY from the SCHEDULED persisted status
 * the same way the server's `effectiveStatus` does — see the plan's §7.1. */
private fun AdminChallenge.dashboardSection(now: Instant): DashboardSection = when (status) {
    ChallengeAdminStatus.DRAFT -> DashboardSection.DRAFTS
    ChallengeAdminStatus.SCHEDULED -> when {
        now.isBefore(startsAt) -> DashboardSection.SCHEDULED
        now.isBefore(endsAt) -> DashboardSection.ACTIVE
        else -> DashboardSection.HISTORY
    }
    ChallengeAdminStatus.CANCELLED, ChallengeAdminStatus.UNKNOWN -> DashboardSection.HISTORY
}

@Composable
private fun AdminChallengesList(
    uiState: AdminChallengesUiState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val now = remember(uiState.challenges) { Instant.now() }
    val grouped = remember(uiState.challenges, now) {
        uiState.challenges.groupBy { it.dashboardSection(now) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 13.dp.actScaled(), bottom = 40.dp.actScaled()),
    ) {
        for (section in DashboardSection.entries) {
            val challenges = grouped[section]
            if (challenges.isNullOrEmpty()) continue

            item(key = "section-${section.name}") {
                SectionLabel(section.label)
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
            }
            items(challenges, key = { "${section.name}-${it.id}" }) { challenge ->
                ChallengeRow(challenge)
                Spacer(modifier = Modifier.height(12.dp.actScaled()))
            }
        }

        if (uiState.isPaging) {
            item(key = "paging") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp.actScaled()),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = SectionLabelColor,
        fontSize = 11.sp.actScaledText(),
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ChallengeRow(challenge: AdminChallenge) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, CardBorder, CardShape)
            .background(CardFill)
            .padding(16.dp.actScaled()),
    ) {
        Text(
            text = challenge.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(4.dp.actScaled()))
        Text(
            text = "${challenge.status.name} · ${challenge.requiredPosts} posts · ${challenge.rewardPoints} pts",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(4.dp.actScaled()))
        Text(
            text = "Created ${challenge.createdAt.toRelativeTime()}",
            color = SectionLabelColor,
            fontSize = 12.sp.actScaledText(),
        )
    }
}

@Composable
private fun CreateChallengeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CreateButtonFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp.actScaled(), vertical = 8.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(18.dp.actScaled()),
        )
        Spacer(modifier = Modifier.width(4.dp.actScaled()))
        Text(
            text = "Create",
            color = Color.Black,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp.actScaledText(),
        )
    }
}
