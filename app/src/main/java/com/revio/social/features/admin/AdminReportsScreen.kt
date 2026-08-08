package com.revio.social.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.ui.Alignment
import com.revio.social.core.ui.components.OfflineStateMessage
import com.revio.social.core.ui.components.StateMessage
import com.revio.social.core.ui.scaling.LocalActivityScale
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.scaling.rememberActivityScale
import com.revio.social.core.ui.theme.Poppins
import com.revio.social.core.util.toRelativeTime
import com.revio.social.data.remote.dto.admin.ModerationDecision
import com.revio.social.data.remote.dto.admin.ReportAdminDto

private val CardFill = Color(0x524E4E4E)
private val CardBorder = Color(0xFF363636)
private val CardShape = RoundedCornerShape(12.dp)
private val TimestampColor = Color(0xFF9D9D9D)
private val DangerRed = Color(0xFFFF5A5F)

/** The moderation queue (GET /api/admin/reports) — previously had no UI at all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportsScreen(
    navController: NavController,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val uiState by viewModel.reportsState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadReports() }

    AppScreenBackgroundWithTopBar(
        title = "Reports queue",
        onBack = { navController.popBackStack() },
    ) {
        when {
            uiState.isLoading -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            uiState.isOffline -> OfflineStateMessage(
                onRetry = { viewModel.retryReports() },
                modifier = Modifier.align(Alignment.Center),
            )
            uiState.errorMessage != null -> StateMessage(
                title = "Couldn't load reports",
                subtitle = uiState.errorMessage,
                actionLabel = "Retry",
                onAction = { viewModel.retryReports() },
                modifier = Modifier.align(Alignment.Center),
            )
            uiState.isEmpty -> StateMessage(
                title = "No pending reports",
                subtitle = "The moderation queue is empty.",
                modifier = Modifier.align(Alignment.Center),
            )
            else -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshReports() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 13.dp.actScaled(), bottom = 40.dp.actScaled()),
                ) {
                    items(uiState.reports, key = { it.id }) { report ->
                        ReportRow(
                            report = report,
                            isResolving = uiState.resolvingReportId == report.id,
                            onUphold = { viewModel.resolveReport(report.id, ModerationDecision.UPHOLD) },
                            onDismissReport = { viewModel.resolveReport(report.id, ModerationDecision.DISMISS) },
                        )
                        Spacer(modifier = Modifier.height(12.dp.actScaled()))
                    }
                }
            }
        }
    }
}

/** Shared top-bar-over-dark-background chrome for the admin panel's screens. */
@Composable
internal fun AppScreenBackgroundWithTopBar(
    title: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    com.revio.social.core.ui.components.AppScreenBackground {
        CompositionLocalProvider(LocalActivityScale provides rememberActivityScale()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp.actScaled()),
            ) {
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
                        text = title,
                        color = Color.White,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Medium,
                        fontSize = 25.sp.actScaledText(),
                    )
                }
                Box(modifier = Modifier.fillMaxSize(), content = content)
            }
        }
    }
}

@Composable
private fun ReportRow(
    report: ReportAdminDto,
    isResolving: Boolean,
    onUphold: () -> Unit,
    onDismissReport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, CardBorder, CardShape)
            .background(CardFill)
            .padding(16.dp.actScaled()),
    ) {
        Text(
            text = report.reason.name.replace('_', ' '),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(4.dp.actScaled()))
        Text(
            text = "Post ${report.postId}",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp.actScaledText(),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(4.dp.actScaled()))
        Text(
            text = report.createdAt.toRelativeTime(),
            color = TimestampColor,
            fontSize = 12.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(12.dp.actScaled()))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = onDismissReport,
                enabled = !isResolving,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text("Dismiss")
            }
            Spacer(modifier = Modifier.width(8.dp.actScaled()))
            Button(
                onClick = onUphold,
                enabled = !isResolving,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
            ) {
                if (isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text("Uphold (remove)", color = Color.White)
                }
            }
        }
    }
}
