package com.revio.app.features.leaderboard

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.revio.app.core.navigation.Screen
import com.revio.app.core.ui.components.AppScreenBackground
import com.revio.app.core.ui.components.FeedNavItem
import com.revio.app.core.ui.components.FloatingBottomNav
import com.revio.app.core.ui.components.StateMessage
import com.revio.app.features.feed.components.rememberPostCreationLauncher
import com.revio.app.features.leaderboard.components.CurrentUserLeaderboardCard
import com.revio.app.features.leaderboard.components.LeaderboardUserRow
import com.revio.app.features.leaderboard.components.PodiumSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    navController: NavHostController,
    viewModel: LeaderboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val openPostCreation = rememberPostCreationLauncher(navController)
    val hazeState = remember { HazeState() }

    val onUserClick: (LeaderboardEntry) -> Unit = { entry ->
        if (entry.userId == uiState.currentUser?.entry?.userId) {
            navController.navigate(Screen.Profile.route) {
                popUpTo(Screen.Feed.route) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(Screen.Profile.createRoute(entry.userId))
        }
    }

    AppScreenBackground(
        foreground = {
            FloatingBottomNav(
                selected = FeedNavItem.Leaderboard,
                profilePictureUrl = uiState.navbarAvatarUrl,
                onHome = {
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Feed.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onLeaderboard = { /* already here */ },
                onPlus = openPostCreation,
                onActivity = {
                    navController.navigate(Screen.Activity.route) {
                        popUpTo(Screen.Feed.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
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
                    title = "Couldn't load the leaderboard",
                    subtitle = uiState.errorMessage,
                    actionLabel = "Retry",
                    onAction = { viewModel.retry() },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            uiState.podium.isEmpty() && uiState.rest.isEmpty() -> {
                StateMessage(
                    title = "No leaderboard yet",
                    subtitle = "Rankings will appear once spotters start earning points.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp, top = 13.dp, start = 10.dp, end = 10.dp),
                    ) {
                        uiState.currentUser?.let { standing ->
                            item {
                                CurrentUserLeaderboardCard(
                                    standing = standing,
                                    onAvatarClick = { onUserClick(standing.entry) },
                                )
                            }
                        }

                        if (uiState.podium.size >= 3) {
                            item {
                                PodiumSection(
                                    podium = uiState.podium,
                                    onUserClick = onUserClick,
                                )
                            }
                        }

                        items(uiState.rest, key = { it.userId }) { entry ->
                            LeaderboardUserRow(
                                entry = entry,
                                onAvatarClick = { onUserClick(entry) },
                                modifier = Modifier.padding(
                                    vertical = 4.dp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
