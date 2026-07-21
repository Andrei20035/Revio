package com.revio.app.features.profile.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.revio.app.core.ui.components.CustomSnackbar
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.revio.app.R
import com.revio.app.core.navigation.Screen
import com.revio.app.core.ui.components.AppScreenBackground
import com.revio.app.core.ui.components.FeedNavItem
import com.revio.app.core.ui.components.FloatingBottomNav
import com.revio.app.core.ui.components.StateMessage
import com.revio.app.core.ui.components.shimmer
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.CompositionLocalProvider
import com.revio.app.core.ui.scaling.LocalProfileDashScale
import com.revio.app.core.ui.scaling.LocalProfileDashVSpacingScale
import com.revio.app.core.ui.scaling.dashScaled
import com.revio.app.core.ui.scaling.dashScaledText
import com.revio.app.core.ui.scaling.dashScaledV
import com.revio.app.core.ui.scaling.rememberProfileDashScale
import com.revio.app.core.ui.scaling.rememberProfileDashVSpacingScale
import com.revio.app.features.feed.components.CommentsSheet
import com.revio.app.features.feed.components.rememberPostCreationLauncher

// Figma tokens — ProfileDashboardScreen (node 790:1216, frame 402×874dp)
private val CardSurface        = Color(0xFF131929)   // tile / button background (unchanged)
private val StatsCardSurface   = Color(0xFF1C1C1C)   // Figma StatsCard fill
private val StatsCardBorder    = Color(0xFF545454)   // Figma StatsCard border 1dp
private val TextMuted          = Color(0xFF707070)   // Figma location / label color
private val ProfileAccent      = Color(0xFF34D7C4)
private val BadgeBackground    = Color(0xFF242424)   // Figma Early Spotter badge fill

// Reference dimensions for bottom clearance (Pixel 9 Pro baseline), matching FeedScreen's pattern.
private val RefNavBarHeight = 64.dp
private val RefNavBottomPadding = 16.dp

private enum class TileState { Loading, Success, Error }

@Composable
fun ProfileDashboardScreen(
    navController: NavController,
    viewModel: ProfileDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    val openPostCreation = rememberPostCreationLauncher(navController)
    val hazeState = remember { HazeState() }

    // Refresh the grid after a post is created from this screen.
    LaunchedEffect(Unit) {
        navController.currentBackStackEntry?.savedStateHandle
            ?.getStateFlow("post_created", false)
            ?.collect { created ->
                if (created) {
                    navController.currentBackStackEntry?.savedStateHandle?.set("post_created", false)
                    viewModel.onPostCreated()
                }
            }
    }

    // Refresh the grid after a post is edited from this screen.
    LaunchedEffect(Unit) {
        navController.currentBackStackEntry?.savedStateHandle
            ?.getStateFlow("post_updated", false)
            ?.collect { updated ->
                if (updated) {
                    navController.currentBackStackEntry?.savedStateHandle?.set("post_updated", false)
                    viewModel.refresh()
                }
            }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    CompositionLocalProvider(
        LocalProfileDashScale provides rememberProfileDashScale(),
        LocalProfileDashVSpacingScale provides rememberProfileDashVSpacingScale(),
    ) {
    AppScreenBackground(
        foreground = {
            if (uiState.isOwnProfile) {
                FloatingBottomNav(
                    selected = FeedNavItem.Profile,
                    profilePictureUrl = uiState.user?.profilePicturePath,
                    hazeState = hazeState,
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
                    onActivity = {
                        navController.navigate(Screen.Activity.route) {
                            popUpTo(Screen.Feed.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onProfile = { /* already here */ },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                )
            }

            // One-shot feedback — auto-dismisses after a short delay.
            uiState.userMessage?.let { message ->
                LaunchedEffect(message) {
                    delay(3000)
                    viewModel.consumeUserMessage()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp),
                ) {
                    CustomSnackbar(message = message)
                }
            }
        },
    ) {
        val navInsetDp = with(LocalDensity.current) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        }
        val bottomClearance = if (uiState.isOwnProfile) {
            navInsetDp + RefNavBarHeight.dashScaled() + RefNavBottomPadding.dashScaled()
        } else {
            navInsetDp
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(4.dp.dashScaled()),
            verticalArrangement = Arrangement.spacedBy(4.dp.dashScaledV()),
            contentPadding = PaddingValues(bottom = bottomClearance, start = 10.dp, end = 10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Header: profile row ──────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileHeaderSection(
                    uiState = uiState,
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onBackClick = { navController.popBackStack() },
                    onEarlySpotterClick = { viewModel.showEarlySpotterInfo() },
                )
            }

            // ── Header: stats card ───────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                StatsCard(uiState = uiState)
            }

            // ── Loading / empty / error states ───────────────────────────
            when {
                uiState.isLoadingInitial -> items(3) {
                    ProfileGridSkeletonTile()
                }

                uiState.errorMessage != null && uiState.isEmpty -> item(span = { GridItemSpan(maxLineSpan) }) {
                    StateMessage(
                        title = "Couldn't load your posts",
                        actionLabel = "Retry",
                        onAction = { viewModel.retry() },
                        accentColor = ProfileAccent,
                        verticalPadding = 48.dp,
                        titleFontSize = 13.sp,
                    )
                }

                uiState.isEmpty && !uiState.isLoadingUser -> item(span = { GridItemSpan(maxLineSpan) }) {
                    StateMessage(
                        title = "No spots yet",
                        subtitle = "Spots you post will show up here.",
                        titleColor = TextMuted,
                        verticalPadding = 48.dp,
                        titleFontSize = 15.sp,
                    )
                }

                else -> {
                    items(uiState.posts, key = { it.id }) { post ->
                        ProfilePostTile(
                            imageUrl = post.imageUrl,
                            contentDescription = "${post.brand} ${post.model}",
                            onClick = { viewModel.onPostClick(post.id) },
                        )
                    }

                    if (uiState.isLoadingMore || uiState.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (uiState.isLoadingMore) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        } // end PullToRefreshBox

        // See-post overlay — shown when a grid image is tapped.
        uiState.selectedPost?.let { post ->
            SeePostOverlay(
                post = post,
                isLikeInFlight = post.id in uiState.likeInFlight,
                isDeleting = uiState.deleteInFlight == post.id,
                showDeleteConfirm = uiState.showDeleteConfirm,
                onLikeToggle = { viewModel.onLikeToggle(post.id) },
                onOpenComments = { viewModel.openComments(post.id) },
                onEditClick = {
                    viewModel.clearSelectedPost()
                    navController.navigate(Screen.ImageUpload.createEditRoute(post.id.toString()))
                },
                onDeleteClick = { viewModel.requestDeletePost() },
                onConfirmDelete = { viewModel.confirmDeletePost() },
                onDismissDeleteConfirm = { viewModel.dismissDeleteConfirm() },
                onDismiss = { viewModel.clearSelectedPost() },
                canDelete = uiState.isOwnProfile,
            )
        }

        // Early Spotter info overlay — opened from the badge pill.
        val esNumber = uiState.user?.earlySpotterNumber
        if (uiState.showEarlySpotterInfo && uiState.user?.isEarlySpotter == true && esNumber != null) {
            EarlySpotterInfoOverlay(
                number = esNumber,
                onDismiss = { viewModel.dismissEarlySpotterInfo() },
            )
        }

        // Comments sheet — opened from the see-post overlay.
        uiState.commentsSheet?.let { sheet ->
            CommentsSheet(
                state = sheet,
                onDismiss = { viewModel.closeComments() },
                onDraftChange = { viewModel.onCommentDraftChange(it) },
                onSend = { viewModel.submitComment() },
                onRetry = { viewModel.retryLoadComments() },
            )
        }
    }
    } // end CompositionLocalProvider(LocalProfileDashScale)
}

@Composable
private fun ProfileGridSkeletonTile() {
    Box(
        modifier = Modifier
            .aspectRatio(121f / 154f)
            .shimmer(RoundedCornerShape(10.dp)),
    )
}

@Composable
private fun ProfilePostTile(
    imageUrl: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    var tileState by remember(imageUrl) { mutableStateOf(TileState.Loading) }
    Box(
        modifier = Modifier
            .aspectRatio(121f / 154f)
            .clip(RoundedCornerShape(8.dp))
            .background(CardSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { tileState = TileState.Success },
            onError = { tileState = TileState.Error },
        )
        if (tileState == TileState.Loading) {
            Box(Modifier.matchParentSize().shimmer(RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun ProfileHeaderSection(
    uiState: ProfileDashboardUiState,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit,
    onEarlySpotterClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp.dashScaledV()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar — Figma: 121×121dp circular
        val avatarUrl = uiState.user?.profilePicturePath
        if (uiState.user == null) {
            // User data not loaded yet — shimmer only while loading.
            Box(
                modifier = Modifier
                    .size(121.dp.dashScaled())
                    .shimmer(CircleShape),
            )
        } else if (avatarUrl.isNullOrBlank()) {
            Image(
                painter = painterResource(R.drawable.profile_picture),
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(121.dp.dashScaled())
                    .clip(CircleShape),
            )
        } else {
            var avatarState by remember(avatarUrl) { mutableStateOf(TileState.Loading) }
            Box(
                modifier = Modifier
                    .size(121.dp.dashScaled())
                    .clip(CircleShape),
            ) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(R.drawable.profile_picture),
                    fallback = painterResource(R.drawable.profile_picture),
                    error = painterResource(R.drawable.profile_picture),
                    onSuccess = { avatarState = TileState.Success },
                    onError = { avatarState = TileState.Error },
                )
                if (avatarState == TileState.Loading) {
                    Box(Modifier.matchParentSize().shimmer(CircleShape))
                }
            }
        }

        Spacer(modifier = Modifier.width(17.dp.dashScaled()))

        // Username + location + badge
        if (uiState.isLoadingUser) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp.dashScaledV())) {
                Box(
                    modifier = Modifier
                        .width(100.dp.dashScaled())
                        .height(16.dp.dashScaledV())
                        .shimmer(RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .width(70.dp.dashScaled())
                        .height(12.dp.dashScaledV())
                        .shimmer(RoundedCornerShape(4.dp))
                )
            }
        } else {
            Column {
                Text(
                    text = uiState.user?.username ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp.dashScaledText(),
                )

                val country = uiState.user?.country?.takeIf { it.isNotBlank() }
                if (country != null) {
                    Spacer(modifier = Modifier.height(7.dp.dashScaledV()))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_gps),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp.dashScaled()),
                        )
                        Spacer(modifier = Modifier.width(2.dp.dashScaled()))
                        Text(
                            text = country,
                            color = TextMuted,
                            fontSize = 15.sp.dashScaledText(),
                        )
                    }
                }

                val esNumber = uiState.user?.earlySpotterNumber
                if (uiState.user?.isEarlySpotter == true && esNumber != null) {
                    Spacer(modifier = Modifier.height(10.dp.dashScaledV()))
                    EarlySpotterBadge(number = esNumber, onClick = onEarlySpotterClick)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .align(Alignment.Top)
                .size(43.dp.dashScaled())
                .clip(CircleShape)
                .background(CardSurface),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.isOwnProfile) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(27.dp),
                    )
                }
            } else {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(27.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EarlySpotterBadge(number: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .background(BadgeBackground)
            .padding(horizontal = 10.dp.dashScaled(), vertical = 3.dp.dashScaledV()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.early_spotter_badge),
            contentDescription = null,
            modifier = Modifier.size(16.dp.dashScaled()),
        )
        Spacer(modifier = Modifier.width(5.dp.dashScaled()))
        Text(
            text = "#$number Early Spotter",
            color = Color.White,
            fontSize = 10.sp.dashScaledText(),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatsCard(uiState: ProfileDashboardUiState) {
    val cardRadius = 12.dp.dashScaled()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp.dashScaledV())
            .border(1.dp, StatsCardBorder, RoundedCornerShape(cardRadius))
            .clip(RoundedCornerShape(cardRadius))
            .background(StatsCardSurface)
            .height(77.dp.dashScaled()),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (uiState.isLoadingUser) {
            StatSkeletonItem()
            StatDivider()
            StatSkeletonItem()
            StatDivider()
            StatSkeletonItem()
        } else {
            StatItem(label = "SpotScore", value = "${uiState.user?.spotScore ?: 0}")
            StatDivider()
            StreakItem(days = uiState.streakDays)
            StatDivider()
            StatItem(label = "Spots", value = "${uiState.postCount}")
        }
    }
}

@Composable
private fun StatSkeletonItem() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(48.dp.dashScaled())
                .height(14.dp.dashScaledV())
                .shimmer(RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(4.dp.dashScaledV()))
        Box(
            modifier = Modifier
                .width(36.dp.dashScaled())
                .height(18.dp.dashScaledV())
                .shimmer(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = TextMuted, fontSize = 16.sp.dashScaledText(), lineHeight = 16.sp.dashScaledText())
        Spacer(modifier = Modifier.height(4.dp.dashScaledV()))
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp.dashScaledText(), lineHeight = 20.sp.dashScaledText())
    }
}

@Composable
private fun StreakItem(days: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Streak", color = TextMuted, fontSize = 16.sp.dashScaledText(), lineHeight = 16.sp.dashScaledText())
        Spacer(modifier = Modifier.height(4.dp.dashScaledV()))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.fire),
                contentDescription = null,
                modifier = Modifier.size(17.dp.dashScaled()),
            )
            Spacer(modifier = Modifier.width(3.dp.dashScaled()))
            Text(
                text = "$days ${if (days == 1) "Day" else "Days"}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp.dashScaledText(),
                lineHeight = 20.sp.dashScaledText()
            )
        }
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp.dashScaled())
            .background(Color.White.copy(alpha = 0.08f))
    )
}
