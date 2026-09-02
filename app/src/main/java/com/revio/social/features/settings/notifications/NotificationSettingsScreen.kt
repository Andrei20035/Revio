package com.revio.social.features.settings.notifications

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.revio.social.core.ui.components.AppScreenBackground
import com.revio.social.core.ui.scaling.LocalActivityScale
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.scaling.rememberActivityScale
import com.revio.social.core.ui.theme.Poppins

private val CardBg = Color(0x3DD9D9D9)
private val SectionLabelColor = Color(0xFF8D8D8D)
private val ItemTextColor = Color.White
private val WarningColor = Color(0xFFF0AB25)

@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionRequestResult(granted)
    }

    // Reflects a trip to Android Settings (permission revoked, or a channel blocked) at revival.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSystemState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAppNotificationSettings() {
        viewModel.logSettingsOpened("settings_row")
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }

    fun openChannelSettings(channelId: String) {
        viewModel.logSettingsOpened("blocked_hint")
        context.startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        )
    }

    AppScreenBackground {
        CompositionLocalProvider(LocalActivityScale provides rememberActivityScale()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp.actScaled()),
            ) {
                // ── Top bar ──────────────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp.actScaled()),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp.actScaled()))
                    Text(
                        text = "Notifications",
                        color = Color.White,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Medium,
                        fontSize = 25.sp.actScaledText(),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp.actScaled()))

                SystemNotificationsRow(
                    status = uiState.systemStatus,
                    onClick = {
                        if (uiState.systemStatus == SystemNotificationsStatus.NOT_ENABLED &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ) {
                            viewModel.logPermissionRequested()
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openAppNotificationSettings()
                        }
                    },
                )

                Spacer(modifier = Modifier.height(24.dp.actScaled()))

                Text(
                    text = "Preferences",
                    modifier = Modifier.padding(start = 10.dp),
                    color = SectionLabelColor,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp.actScaledText(),
                )

                Spacer(modifier = Modifier.height(8.dp.actScaled()))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBg),
                ) {
                    NotificationCategoryRow(
                        label = "Likes",
                        state = uiState.likes,
                        interactive = uiState.switchesInteractive,
                        onCheckedChange = viewModel::setLikesEnabled,
                        onBlockedHintClick = { openChannelSettings("likes") },
                    )
                    NotificationCategoryRow(
                        label = "Comments",
                        state = uiState.comments,
                        interactive = uiState.switchesInteractive,
                        onCheckedChange = viewModel::setCommentsEnabled,
                        onBlockedHintClick = { openChannelSettings("comments") },
                    )
                    NotificationCategoryRow(
                        label = "Community discoveries",
                        state = uiState.discovery,
                        interactive = uiState.switchesInteractive,
                        onCheckedChange = viewModel::setDiscoveryEnabled,
                        onBlockedHintClick = { openChannelSettings("discovery") },
                    )
                    NotificationCategoryRow(
                        label = "Leaderboard & reminders",
                        state = uiState.reminders,
                        interactive = uiState.switchesInteractive,
                        onCheckedChange = viewModel::setRemindersEnabled,
                        onBlockedHintClick = { openChannelSettings("reminders") },
                    )
                }

                Spacer(modifier = Modifier.height(6.dp.actScaled()))
                // States 1/2/5 — switches are disabled because system notifications aren't fully on.
                if (!uiState.switchesInteractive) {
                    Text(
                        text = "Enable notifications to use these.",
                        color = SectionLabelColor,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp.actScaledText(),
                        modifier = Modifier.padding(start = 10.dp.actScaled()),
                    )
                    Spacer(modifier = Modifier.height(4.dp.actScaled()))
                }
                Text(
                    text = "Account and safety notices are always delivered.",
                    color = SectionLabelColor,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp.actScaledText(),
                    modifier = Modifier.padding(start = 10.dp.actScaled()),
                )

                Spacer(modifier = Modifier.height(32.dp.actScaled()))
            }
        }
    }
}

@Composable
private fun SystemNotificationsRow(
    status: SystemNotificationsStatus,
    onClick: () -> Unit,
) {
    val statusText = when (status) {
        SystemNotificationsStatus.NOT_ENABLED -> "Not enabled"
        SystemNotificationsStatus.DISABLED -> "Disabled"
        SystemNotificationsStatus.ENABLED -> "Enabled"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp.actScaled())
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Phone notification settings",
            color = ItemTextColor,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp.actScaledText(),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = statusText,
            color = SectionLabelColor,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp.actScaledText(),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = SectionLabelColor,
            modifier = Modifier.width(20.dp.actScaled()),
        )
    }
}

@Composable
private fun NotificationCategoryRow(
    label: String,
    state: NotificationCategoryUiState,
    interactive: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onBlockedHintClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp.actScaled(), vertical = 10.dp.actScaled()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = ItemTextColor,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp.actScaledText(),
                modifier = Modifier.weight(1f),
            )
            Switch(checked = state.enabled, enabled = interactive, onCheckedChange = onCheckedChange)
        }
        // State 6 — the Revio toggle stays on, but Android silently drops this channel.
        if (interactive && state.blockedByChannel) {
            Spacer(modifier = Modifier.height(4.dp.actScaled()))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onBlockedHintClick),
            ) {
                Text(
                    text = "Blocked in Android settings",
                    color = WarningColor,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp.actScaledText(),
                )
            }
        }
    }
}
