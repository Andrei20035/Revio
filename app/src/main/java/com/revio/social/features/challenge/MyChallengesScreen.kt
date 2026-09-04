package com.revio.social.features.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.revio.social.core.navigation.Screen
import com.revio.social.core.ui.components.StateMessage
import com.revio.social.core.ui.theme.Poppins
import com.revio.social.data.model.ChallengeHistoryItem
import com.revio.social.features.challenge.components.ChallengeCard
import com.revio.social.features.challenge.components.ChallengeHistoryRow
import com.revio.social.features.challenge.components.ChallengeSummaryStrip
import com.revio.social.features.feed.components.rememberPostCreationLauncher
import java.time.Instant
import kotlinx.coroutines.flow.distinctUntilChanged

private val ScreenBackground = Color(0xFF05081D)
private val SectionLabelColor = Color(0xFF707070)

/** [ChallengeCard] needs a live [ChallengeUiState.Active], not a history row — this converts the
 * caller's active [ChallengeHistoryItem] into the same shape the Feed card renders, so the two
 * stay visually identical without duplicating the card. Visibility is `internal` (not `private`)
 * so it's directly unit-testable — see `MyChallengesScreenStateTest`. */
internal fun ChallengeHistoryItem.toActiveCardState(now: Instant): ChallengeUiState.Active = ChallengeUiState.Active(
    challengeId = challenge.id,
    titleLine = "Spot ${challenge.requiredPosts} ${challenge.targetFamilyBrand} ${challenge.targetFamilyName}",
    contributionCount = progress.contributionCount,
    requiredPosts = challenge.requiredPosts,
    rewardPoints = challenge.rewardPoints,
    rewardState = progress.rewardState,
    participantState = progress.participantState,
    effectiveStatus = effectiveStatus,
    endsAt = challenge.endsAt,
    remaining = remainingTimeAt(now, challenge.endsAt),
    isStale = false,
)

/** Lists the caller's own challenge participation — summary, active challenge, and history. See
 * the plan's §3 wireframe and §6/§7 for the underlying state machine. */
@Composable
fun MyChallengesScreen(
    navController: NavController,
    viewModel: MyChallengesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openPostCreation = rememberPostCreationLauncher(navController)
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
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "My Challenges",
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                )
            }

            when (val state = uiState) {
                MyChallengesUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color(0xFF34D7C4))
                }

                MyChallengesUiState.Empty -> StateMessage(
                    title = "No challenges yet",
                    subtitle = "Challenges run on weekends. Join the next one to earn points.",
                )

                is MyChallengesUiState.Error -> StateMessage(
                    title = "Couldn't load your challenges",
                    subtitle = state.message,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )

                is MyChallengesUiState.Content -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    item(key = "summary") {
                        ChallengeSummaryStrip(summary = state.summary)
                    }

                    val active = state.active
                    if (active != null) {
                        item(key = "active-label") {
                            Spacer(modifier = Modifier.height(20.dp))
                            SectionLabel("ACTIVE NOW")
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        item(key = "active-${active.challenge.id}") {
                            ChallengeCard(
                                state = active.toActiveCardState(Instant.now()),
                                onCardClick = {
                                    navController.navigate(Screen.ChallengeDetail.createRoute(active.challenge.id))
                                },
                                onSpotNow = openPostCreation.openCamera,
                            )
                        }
                    }

                    if (state.past.isNotEmpty()) {
                        item(key = "past-label") {
                            Spacer(modifier = Modifier.height(20.dp))
                            SectionLabel("PAST")
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(state.past, key = { it.challenge.id }) { historyItem ->
                            ChallengeHistoryRow(
                                item = historyItem,
                                onClick = {
                                    navController.navigate(Screen.ChallengeDetail.createRoute(historyItem.challenge.id))
                                },
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }

                    if (state.isPaging) {
                        item(key = "paging") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Color(0xFF34D7C4))
                            }
                        }
                    }
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
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium,
    )
}
