package com.example.carspotter.features.feed

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.carspotter.R
import com.example.carspotter.core.navigation.Screen
import com.example.carspotter.core.ui.components.AppScreenBackground
import com.example.carspotter.core.ui.components.CustomSnackbar
import com.example.carspotter.core.ui.components.FeedNavItem
import com.example.carspotter.core.ui.components.FloatingBottomNav
import com.example.carspotter.core.ui.components.LikeIcon
import com.example.carspotter.core.ui.components.formatCount
import com.example.carspotter.core.ui.components.interactionCountWidth
import com.example.carspotter.core.ui.theme.Poppins
import com.example.carspotter.data.model.FeedPost
import com.example.carspotter.data.model.ReportReason
import com.example.carspotter.features.feed.components.CarLocationRow
import com.example.carspotter.features.feed.components.CommentsSheet
import com.example.carspotter.features.feed.components.FeedPostSkeleton
import com.example.carspotter.features.feed.components.PostOptionsMenu
import com.example.carspotter.features.feed.components.SubmitReportDialog
import com.example.carspotter.features.feed.components.rememberPostCreationLauncher
import androidx.compose.runtime.CompositionLocalProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.example.carspotter.core.ui.scaling.LocalFeedScale
import com.example.carspotter.core.ui.scaling.LocalFeedVSpacingScale
import com.example.carspotter.core.ui.scaling.rememberFeedScale
import com.example.carspotter.core.ui.scaling.rememberFeedVSpacingScale
import com.example.carspotter.core.ui.scaling.scaled
import com.example.carspotter.core.ui.scaling.scaledText
import com.example.carspotter.core.ui.scaling.scaledV
import kotlinx.coroutines.delay

private val FeedAccent = Color(0xFF34D7C4)
// Discreet dark placeholder shown behind a post image while it loads.
private val ImagePlaceholder = Color(0xFF11162E)
// Figma horizontal margin for cards: 375dp image inside the 402dp frame → ~13dp each side.
private val CardHorizontalPadding = 10.dp
// Number of shimmer skeleton cards shown during the initial feed load.
private const val SKELETON_COUNT = 3

// Reference dimensions for FeedPostCard header (Pixel 9 Pro baseline, scale == 1.0).
private val RefAvatarSize = 37.dp
private val RefAvatarTouchPadding = 5.5.dp
private val RefAvatarUsernameSpacing = 8.dp
private val RefHeaderUsernameFontSize = 13.sp
private val RefUsernameCarSpacing = 2.dp
private val RefHeaderBottomSpacing = 9.dp

// Reference dimensions for bottom clearance (Pixel 9 Pro baseline).
// 140dp total = nav inset (dynamic) + RefNavBarHeight (64dp) + RefNavBottomPadding (16dp).
// Only the nav height + bottom padding scale; the system inset is always left untouched.
private val RefNavBarHeight = 64.dp
private val RefNavBottomPadding = 16.dp

// Reference dimensions for caption (Pixel 9 Pro baseline).
private val RefCaptionTopSpacing = 8.dp
private val RefCaptionFontSize = 14.sp
private val RefCaptionIndent = 12.dp

// Reference dimensions for FeedMessage (Pixel 9 Pro baseline).
private val RefFeedMessagePaddingV = 80.dp
private val RefFeedMessageTitleFontSize = 18.sp
private val RefFeedMessageSubtitleFontSize = 14.sp
private val RefFeedMessageTitleSubtitleSpacing = 6.dp
private val RefFeedMessageActionSpacing = 12.dp

// Reference dimensions for FeedFooter (Pixel 9 Pro baseline).
private val RefFooterPaddingV = 20.dp
private val RefFooterSpinnerSize = 28.dp
private val RefFooterFontSize = 13.sp

// Reference dimensions for FeedPostCard image + engagement row (Pixel 9 Pro baseline).
private val RefImageCornerRadius = 18.dp
private val RefImageBottomSpacing = 14.dp
private val RefEngagementIndent = 12.dp
private val RefEngagementGroupSpacing = 12.dp
private val RefLikeIconSize = 30.dp
private val RefCommentIconSize = 26.dp
private val RefIconCountSpacing = 6.dp
private val RefEngagementCountFontSize = 14.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    navController: NavController,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val openPostCreation = rememberPostCreationLauncher(navController)
    val hazeState = remember { HazeState() }

    // "Submit Report" confirmation — driven entirely by the ViewModel (UDF).
    uiState.reportDialog?.let { dialog ->
        SubmitReportDialog(
            reason = dialog.reason,
            isSubmitting = dialog.isSubmitting,
            onConfirm = { viewModel.confirmReport() },
            onDismiss = { viewModel.dismissReportDialog() },
        )
    }

    // Instagram-style comments overlay — opens over the feed, driven by the ViewModel (UDF).
    uiState.commentsSheet?.let { sheet ->
        CommentsSheet(
            state = sheet,
            onDismiss = { viewModel.closeComments() },
            onDraftChange = { viewModel.onCommentDraftChange(it) },
            onSend = { viewModel.submitComment() },
            onRetry = { viewModel.retryLoadComments() },
        )
    }

    val listState = rememberLazyListState()
    // Prefetch the next page once the last few items become visible.
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    CompositionLocalProvider(
        LocalFeedScale provides rememberFeedScale(),
        LocalFeedVSpacingScale provides rememberFeedVSpacingScale(),
    ) {
    AppScreenBackground(
        foreground = {
            FloatingBottomNav(
                selected = FeedNavItem.Home,
                profilePictureUrl = uiState.currentUser?.profilePicturePath,
                onHome = { /* already on feed */ },
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

            // One-shot feedback (e.g. report submitted) — auto-dismisses after a short delay.
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
        // Bottom clearance = system nav inset (untouched) + scaled nav bar height + scaled bottom padding.
        val navInsetDp = with(LocalDensity.current) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        }
        val bottomClearance = navInsetDp + RefNavBarHeight.scaled() + RefNavBottomPadding.scaled()

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = CardHorizontalPadding),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    // Clearance so the floating navbar never covers the last card.
                    bottom = bottomClearance,
                ),
            ) {
                when {
                    uiState.isLoadingInitial -> items(SKELETON_COUNT, key = { "skeleton-$it" }) {
                        FeedPostSkeleton()
                        Spacer(modifier = Modifier.height(30.dp))
                    }

                    uiState.isEmpty && uiState.errorMessage != null -> item(key = "error") {
                        FeedMessage(
                            title = "Couldn't load the feed",
                            subtitle = uiState.errorMessage,
                            actionLabel = "Retry",
                            onAction = { viewModel.retry() },
                        )
                    }

                    uiState.isEmpty -> item(key = "empty") {
                        FeedMessage(
                            title = "No spots yet",
                            subtitle = "Be the first to share a find.",
                        )
                    }

                    else -> {
                        items(uiState.feedPosts, key = { it.id }) { post ->
                            FeedPostCard(
                                post = post,
                                onLikeToggle = { viewModel.onLikeToggle(post.id) },
                                onOpenComments = { viewModel.openComments(post.id) },
                                onShare = { sharePost(context, post) },
                                onReportReasonSelected = { reason ->
                                    viewModel.onReportReasonSelected(post.id, reason)
                                },
                                onAuthorClick = {
                                    if (post.userId == uiState.currentUser?.id) {
                                        navController.navigate(Screen.Profile.route) {
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navController.navigate(Screen.Profile.createRoute(post.userId))
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                        item(key = "footer") {
                            FeedFooter(
                                isLoadingMore = uiState.isLoadingMore,
                                hasMore = uiState.hasMore,
                                loadMoreError = uiState.errorMessage,
                                onRetry = { viewModel.retry() },
                            )
                        }
                    }
                }
            }
        }
    }
    } // end CompositionLocalProvider(LocalFeedScale)
}

@Composable
private fun FeedPostCard(
    post: FeedPost,
    onLikeToggle: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onReportReasonSelected: (ReportReason) -> Unit,
    onAuthorClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ---- Header: avatar · username · car (+ location) · more ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuthorAvatar(url = post.authorProfilePictureUrl, username = post.username, isEarlySpotter = post.authorShowEarlySpotter, onClick = onAuthorClick)
            Spacer(modifier = Modifier.width(RefAvatarUsernameSpacing.scaled()))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.username,
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = RefHeaderUsernameFontSize.scaledText(),
                    lineHeight = RefHeaderUsernameFontSize.scaledText(),
                )
                Spacer(modifier = Modifier.height(RefUsernameCarSpacing.scaledV()))
                CarLocationRow(
                    carName = post.carName,
                    location = post.locationLabel,
                )
            }
            PostOptionsMenu(
                onShare = onShare,
                onReportReasonSelected = onReportReasonSelected,
            )
        }

        Spacer(modifier = Modifier.height(RefHeaderBottomSpacing.scaledV()))

        // ---- Main image (375×468 ≈ aspect 0.80, 18dp radius, soft shadow) ----
        val imageCorner = RefImageCornerRadius.scaled()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(375f / 468f)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(imageCorner), clip = false)
                .clip(RoundedCornerShape(imageCorner))
                .background(ImagePlaceholder),
        ) {
            AsyncImage(
                model = post.imageUrl,
                contentDescription = post.carName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(RefImageBottomSpacing.scaledV()))

        // ---- Engagement row (indented ~12dp from the image edge) ----
        // Each count sits in a small adaptive-width slot so the comment group doesn't shift on
        // small count changes (0↔1, 1↔9), while still staying compact for single digits.
        Row(
            modifier = Modifier.padding(start = RefEngagementIndent.scaled()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InteractionItem(count = post.likeCount, onClick = onLikeToggle) {
                // Icon is driven by this post's own server-backed liked state — never hardcoded.
                LikeIcon(liked = post.likedByCurrentUser, size = RefLikeIconSize.scaled())
            }
            Spacer(modifier = Modifier.width(RefEngagementGroupSpacing.scaled()))
            InteractionItem(count = post.commentCount, onClick = onOpenComments) {
                Image(
                    painter = painterResource(R.drawable.comment),
                    contentDescription = "Comments",
                    modifier = Modifier.size(RefCommentIconSize.scaled()),
                )
            }
        }

        // ---- Caption ----
        if (!post.caption.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(RefCaptionTopSpacing.scaledV()))
            Text(
                text = post.caption,
                color = Color.White,
                fontFamily = Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = RefCaptionFontSize.scaledText(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = RefCaptionIndent.scaled()),
            )
        }
    }
}

/**
 * A single feed interaction (icon + count) laid out as `Row { Icon; CountText }`. The whole row
 * is the tap target. The count lives in a small fixed-width slot sized by [interactionCountWidth]
 * so neighbouring groups don't jump as the count changes between small values.
 */
@Composable
private fun InteractionItem(
    count: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val scale = LocalFeedScale.current
    Row(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(RefIconCountSpacing.scaled()))
        Box(modifier = Modifier.width(interactionCountWidth(count, scale))) {
            Text(
                text = formatCount(count),
                color = Color.White,
                fontFamily = Poppins,
                fontWeight = FontWeight.Medium,
                fontSize = RefEngagementCountFontSize.scaledText(),
                maxLines = 1,
                // Tabular figures keep each digit the same width, so the slot stays stable.
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
    }
}

/** Post author's avatar — the real profile picture, falling back to the placeholder. */
@Composable
private fun AuthorAvatar(url: String?, username: String, isEarlySpotter: Boolean = false, onClick: (() -> Unit)? = null) {
    val avatarSize = RefAvatarSize.scaled()
    // Guarantee minimum 48dp touch target regardless of scale: if the scaled padding would make the
    // total (avatar + 2×padding) fall below 48dp, expand the padding to compensate.
    val touchPadding = maxOf(RefAvatarTouchPadding.scaled(), (48.dp - avatarSize) / 2)
    Box(
        modifier = if (onClick != null) {
            Modifier
                .semantics { role = Role.Button; contentDescription = "Open $username's profile" }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(touchPadding)
        } else {
            Modifier
        },
        contentAlignment = Alignment.Center,
    ) {
        // Inner box so the ring overlays the avatar without affecting the outer touch target.
        Box(contentAlignment = Alignment.Center) {
            val imageModifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
            if (url.isNullOrBlank()) {
                Image(
                    painter = painterResource(R.drawable.profile_picture),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier,
                )
            } else {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier,
                    placeholder = painterResource(R.drawable.profile_picture),
                    fallback = painterResource(R.drawable.profile_picture),
                    error = painterResource(R.drawable.profile_picture),
                )
            }
            // es_ring.xml is 37×41dp (taller due to the bottom notch glyph); scale proportionally.
            if (isEarlySpotter) {
                Image(
                    painter = painterResource(R.drawable.es_ring),
                    contentDescription = null,
                    modifier = Modifier
                        .size(avatarSize, avatarSize * (41f / 37f))
                        .offset(y = avatarSize * ((41f / 37f - 1f) / 2f)),
                )
            }
        }
    }
}

@Composable
private fun FeedMessage(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RefFeedMessagePaddingV.scaled()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, fontSize = RefFeedMessageTitleFontSize.scaledText(), fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(RefFeedMessageTitleSubtitleSpacing.scaled()))
            Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = RefFeedMessageSubtitleFontSize.scaledText())
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(RefFeedMessageActionSpacing.scaled()))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = FeedAccent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FeedFooter(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    loadMoreError: String?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RefFooterPaddingV.scaled()),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoadingMore -> CircularProgressIndicator(
                color = FeedAccent,
                modifier = Modifier.size(RefFooterSpinnerSize.scaled()),
            )

            loadMoreError != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Couldn't load more", color = Color.White.copy(alpha = 0.7f), fontSize = RefFooterFontSize.scaledText())
                TextButton(onClick = onRetry) {
                    Text("Retry", color = FeedAccent, fontWeight = FontWeight.SemiBold)
                }
            }

            !hasMore -> Text(
                "You're all caught up",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = RefFooterFontSize.scaledText(),
            )
        }
    }
}

/**
 * Fires the standard Android share sheet (`ACTION_SEND`) for a post — the car label and a link
 * to the photo. This is a client-only action; no backend call is involved.
 */
private fun sharePost(context: Context, post: FeedPost) {
    val shareText = buildString {
        append("Check out this ${post.carName} on CarSpotter")
        if (post.imageUrl.isNotBlank()) {
            append("\n")
            append(post.imageUrl)
        }
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share post"))
}

