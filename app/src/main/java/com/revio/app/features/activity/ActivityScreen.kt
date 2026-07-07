package com.revio.app.features.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.revio.app.core.navigation.Screen
import com.revio.app.core.ui.components.AppScreenBackground
import com.revio.app.core.ui.components.FeedNavItem
import com.revio.app.core.ui.components.FloatingBottomNav
import com.revio.app.core.ui.components.StateMessage
import com.revio.app.core.ui.scaling.LocalActivityScale
import com.revio.app.core.ui.scaling.actScaled
import com.revio.app.core.ui.scaling.actScaledText
import com.revio.app.core.ui.scaling.rememberActivityScale
import com.revio.app.core.ui.theme.Poppins
import com.revio.app.features.activity.components.CommentActivityCard
import com.revio.app.features.activity.components.LeaderboardUpCard
import com.revio.app.features.activity.components.LikeActivityCard
import com.revio.app.features.activity.components.StatCard
import com.revio.app.features.activity.components.StreakCard
import com.revio.app.features.activity.model.ActivityItem
import com.revio.app.features.feed.components.rememberPostCreationLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    navController: NavController,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val openPostCreation = rememberPostCreationLauncher(navController)
    val hazeState = remember { HazeState() }

    AppScreenBackground(
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
                onLeaderboard = {
                    navController.navigate(Screen.Leaderboard.route) {
                        popUpTo(Screen.Feed.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onPlus = openPostCreation,
                onActivity = { /* already here */ },
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        },
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    color = Color.White,
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
                                Text(
                                    text = "Activity",
                                    color = Color.White,
                                    fontFamily = Poppins,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 35.sp.actScaledText(),
                                )
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
                                            is ActivityItem.LeaderboardUpItem -> LeaderboardUpCard(activityItem)
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
    }
}
