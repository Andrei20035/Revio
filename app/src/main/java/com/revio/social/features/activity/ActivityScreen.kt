package com.revio.social.features.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.revio.social.core.activitydot.ActivityDotViewModel
import com.revio.social.core.navigation.Screen
import com.revio.social.core.notices.NoticesUnreadViewModel
import com.revio.social.core.tour.TourHostViewModel
import com.revio.social.core.tour.TourStep
import com.revio.social.core.ui.components.AppScreenBackground
import com.revio.social.core.ui.components.FeedNavItem
import com.revio.social.core.ui.components.FloatingBottomNav
import com.revio.social.core.ui.components.NavSlot
import com.revio.social.core.ui.components.OfflineStateMessage
import com.revio.social.core.ui.components.StateMessage
import com.revio.social.core.ui.tour.TourOverlay
import com.revio.social.core.ui.scaling.LocalActivityScale
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.scaling.rememberActivityScale
import com.revio.social.core.ui.theme.Poppins
import com.revio.social.features.activity.components.CommentActivityCard
import com.revio.social.features.activity.components.LeaderboardUpCard
import com.revio.social.features.activity.components.LikeActivityCard
import com.revio.social.features.activity.components.StatCard
import com.revio.social.features.activity.components.StreakCard
import com.revio.social.features.activity.components.TodayInteractionsInfoOverlay
import com.revio.social.features.activity.model.ActivityItem
import com.revio.social.features.feed.components.rememberPostCreationLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    navController: NavController,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val noticesUnreadViewModel: NoticesUnreadViewModel = hiltViewModel()
    val noticesUnreadCount by noticesUnreadViewModel.unreadCount.collectAsStateWithLifecycle()
    val openPostCreation = rememberPostCreationLauncher(navController)
    val hazeState = remember { HazeState() }
    val tourHostViewModel: TourHostViewModel = hiltViewModel()
    val tourStep by tourHostViewModel.tourController.step.collectAsState()
    var slotBounds by remember { mutableStateOf(emptyMap<NavSlot, Rect>()) }
    val activityDotViewModel: ActivityDotViewModel = hiltViewModel()
    val activityHasDot by activityDotViewModel.hasUnseenActivity.collectAsStateWithLifecycle()

    // Primary trigger (plan §10, Pasul 6, Q4): fires on *any* entry into the Activity
    // destination — tab, deep link, or navigation restoration alike — not only a tab tap.
    LaunchedEffect(Unit) {
        activityDotViewModel.onActivityOpened()
    }

    // pas 3 (docs/plans/avem-un-bug-android-mutable-sky.md) — retries a screen stuck in a
    // network error without depending on a connectivity-transition event that might never fire.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    val goToLeaderboard = {
        navController.navigate(Screen.Leaderboard.route) {
            popUpTo(Screen.Feed.route) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    AppScreenBackground(
        showBottomScrim = true,
        foreground = {
            FloatingBottomNav(
                selected = FeedNavItem.Activity,
                profilePictureUrl = uiState.currentUser?.profilePicturePath,
                onHome = {
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Feed.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onLeaderboard = goToLeaderboard,
                onPlus = openPostCreation.openChooser,
                // Secondary trigger (plan §10, Pasul 6): tapping Activity while already on it
                // doesn't recompose the destination, so LaunchedEffect(Unit) above won't re-fire.
                // Idempotent, so this never conflicts with that primary trigger.
                onActivity = { activityDotViewModel.onActivityOpened() },
                onProfile = {
                    navController.navigate(Screen.Profile.route) {
                        popUpTo(Screen.Feed.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                hazeState = hazeState,
                onSlotBounds = { slot, rect -> slotBounds = slotBounds + (slot to rect) },
                activityHasDot = activityHasDot,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )

            if (tourStep == TourStep.Activity) {
                TourOverlay(
                    step = TourStep.Activity,
                    spotlight = slotBounds[NavSlot.Activity],
                    onAdvance = { tourHostViewModel.tourController.advance() },
                    onPostCta = {},
                )
            }
        },
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            uiState.isOffline -> {
                OfflineStateMessage(
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            uiState.errorMessage != null -> {
                StateMessage(
                    title = "Couldn't load your activity",
                    subtitle = uiState.errorMessage,
                    actionLabel = "Retry",
                    onAction = { viewModel.retry() },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                CompositionLocalProvider(LocalActivityScale provides rememberActivityScale()) {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                bottom = 140.dp,
                                top = 13.dp.actScaled(),
                                start = 10.dp.actScaled(),
                                end = 10.dp.actScaled(),
                            ),
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Activity",
                                        color = Color.White,
                                        fontFamily = Poppins,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 35.sp.actScaledText(),
                                        modifier = Modifier.weight(1f),
                                    )
                                    NotificationsBellButton(
                                        unreadCount = noticesUnreadCount,
                                        onClick = { navController.navigate(Screen.Notices.route) },
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp.actScaled()))
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp.actScaled()),
                                ) {
                                    StatCard(
                                        modifier = Modifier.weight(1f),
                                        title = "Weekly SpotScore",
                                        value = uiState.weeklySpotScore,
                                        isWeeklyScore = true,
                                    )
                                    StatCard(
                                        modifier = Modifier.weight(1f),
                                        title = "Today's Interactions",
                                        value = uiState.todayInteractions,
                                        isWeeklyScore = false,
                                        onInfoClick = { viewModel.showTodayInteractionsInfo() },
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp.actScaled()))
                            }

                            if (uiState.isEmpty) {
                                item {
                                    StateMessage(
                                        title = "No activity yet",
                                        subtitle = "Likes, comments, and streaks will show up here.",
                                        verticalPadding = 24.dp,
                                    )
                                }
                            } else {
                                items(uiState.items, key = { it.id }) { activityItem ->
                                    Column {
                                        when (activityItem) {
                                            is ActivityItem.LikeItem -> LikeActivityCard(activityItem)
                                            is ActivityItem.CommentItem -> CommentActivityCard(activityItem)
                                            is ActivityItem.LeaderboardUpItem -> LeaderboardUpCard(activityItem, onClick = goToLeaderboard)
                                            is ActivityItem.StreakItem -> StreakCard(activityItem)
                                        }
                                        Spacer(modifier = Modifier.height(12.dp.actScaled()))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.showTodayInteractionsInfo) {
            TodayInteractionsInfoOverlay(onDismiss = { viewModel.dismissTodayInteractionsInfo() })
        }
    }
}

/** Entry point into [com.revio.social.features.notifications.NoticesScreen], with an unread-count badge. */
@Composable
private fun NotificationsBellButton(
    unreadCount: Long,
    onClick: () -> Unit,
) {
    Box {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "Notices",
                tint = Color.White,
                modifier = Modifier.size(26.dp.actScaled()),
            )
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp.actScaled(), end = 6.dp.actScaled())
                    .size(8.dp.actScaled())
                    .clip(CircleShape)
                    .background(Color(0xFFF0AB25)),
            )
        }
    }
}
