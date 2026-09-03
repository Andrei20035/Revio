package com.revio.social.features.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.revio.social.core.notices.NoticesUnreadViewModel
import com.revio.social.core.ui.components.AppScreenBackground
import com.revio.social.core.ui.components.OfflineStateMessage
import com.revio.social.core.ui.components.StateMessage
import com.revio.social.core.ui.scaling.LocalActivityScale
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.scaling.rememberActivityScale
import com.revio.social.core.ui.theme.Poppins
import com.revio.social.core.util.toRelativeTime
import com.revio.social.data.remote.dto.notification.NotificationDto
import kotlinx.coroutines.delay

private val CardFill = Color(0x524E4E4E)
private val CardBorder = Color(0xFF363636)
private val CardShape = RoundedCornerShape(12.dp)
private val TimestampColor = Color(0xFF9D9D9D)
private val UnreadDotColor = Color(0xFFF0AB25)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesScreen(
    navController: NavController,
    viewModel: NoticesViewModel = hiltViewModel(),
    noticesUnreadViewModel: NoticesUnreadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Entering Notices marks every unread ACCOUNT notice read server-side (excluding blocking
    // notices, which are only acknowledged through ModerationNoticeHost's dialog) and zeroes the
    // bell badge optimistically.
    LaunchedEffect(Unit) {
        noticesUnreadViewModel.onNoticesOpened()
    }

    // pas 3 (docs/plans/avem-un-bug-android-mutable-sky.md) — retries a screen stuck in a
    // network error without depending on a connectivity-transition event that might never fire.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    LaunchedEffect(uiState.actionErrorMessage) {
        if (uiState.actionErrorMessage != null) {
            delay(3000)
            viewModel.clearActionError()
        }
    }

    AppScreenBackground {
        CompositionLocalProvider(LocalActivityScale provides rememberActivityScale()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp.actScaled()),
            ) {
                NoticesTopBar(onBack = { navController.popBackStack() })

                uiState.actionErrorMessage?.let { message ->
                    ActionErrorBanner(message = message)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading -> CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        uiState.isOffline -> OfflineStateMessage(
                            onRetry = { viewModel.retry() },
                            modifier = Modifier.align(Alignment.Center),
                        )
                        uiState.errorMessage != null -> StateMessage(
                            title = "Couldn't load your notices",
                            subtitle = uiState.errorMessage,
                            actionLabel = "Retry",
                            onAction = { viewModel.retry() },
                            modifier = Modifier.align(Alignment.Center),
                        )
                        uiState.isEmpty -> StateMessage(
                            title = "No notices yet",
                            subtitle = "We'll let you know when something needs your attention.",
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> {
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
                                if (shouldLoadMore) viewModel.loadMore()
                            }

                            PullToRefreshBox(
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = { viewModel.refresh() },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        top = 13.dp.actScaled(),
                                        bottom = 40.dp.actScaled(),
                                    ),
                                ) {
                                    items(uiState.items, key = { it.id }) { notification ->
                                        NotificationRow(
                                            notification = notification,
                                            onClick = { viewModel.onNotificationClicked(notification) },
                                        )
                                        Spacer(modifier = Modifier.height(12.dp.actScaled()))
                                    }
                                    if (uiState.isLoadingMore) {
                                        item {
                                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp.actScaled())) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .size(20.dp.actScaled()),
                                                )
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
    }
}

/** Dismissible failure banner for [NoticesViewModel.markRead] (pas 5.1) — the list underneath stays visible. */
@Composable
private fun ActionErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp.actScaled())
            .clip(CardShape)
            .background(Color(0x33FF5252))
            .padding(horizontal = 14.dp.actScaled(), vertical = 10.dp.actScaled()),
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 13.sp.actScaledText(),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NoticesTopBar(onBack: () -> Unit) {
    Spacer(modifier = Modifier.height(8.dp.actScaled()))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(28.dp.actScaled()),
            )
        }
        Spacer(modifier = Modifier.width(4.dp.actScaled()))
        Text(
            text = "Notices",
            color = Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.Medium,
            fontSize = 25.sp.actScaledText(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NotificationRow(
    notification: NotificationDto,
    onClick: () -> Unit,
) {
    val isUnread = notification.readAt == null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp.actScaled())
            .clip(CardShape)
            .border(1.dp, CardBorder, CardShape)
            .background(CardFill)
            .clickable(enabled = isUnread, onClick = onClick)
            .padding(horizontal = 16.dp.actScaled(), vertical = 12.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isUnread) {
            Box(
                modifier = Modifier
                    .size(8.dp.actScaled())
                    .clip(CircleShape)
                    .background(UnreadDotColor),
            )
            Spacer(modifier = Modifier.width(10.dp.actScaled()))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp.actScaledText(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp.actScaled()))
            Text(
                text = notification.body,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp.actScaledText(),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp.actScaled()))
            Text(
                text = notification.createdAt.toRelativeTime(),
                color = TimestampColor,
                fontSize = 12.sp.actScaledText(),
                maxLines = 1,
            )
        }
    }
}
