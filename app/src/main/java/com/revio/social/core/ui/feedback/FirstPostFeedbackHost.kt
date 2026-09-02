package com.revio.social.core.ui.feedback

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revio.social.core.notifications.NotificationPrepromptCtaAction
import com.revio.social.core.notifications.resolveNotifyMeAction
import com.revio.social.core.ui.components.CustomSnackbar
import com.revio.social.core.ui.components.SnackbarSuccessColor
import com.revio.social.core.ui.overlay.OverlayScrim
import com.revio.social.data.model.FeedbackSurface
import kotlinx.coroutines.delay

/**
 * Hosts the first-post feedback card — and its post-submit confirmation snackbar — anchored
 * above the floating nav bar, the same way [CustomSnackbar] is anchored in `FeedScreen` /
 * `ProfileDashboardScreen`. Meant to be composed inside `AppScreenBackground`'s `foreground`
 * slot (a [BoxScope]) alongside the nav bar.
 *
 * [isBlocked] is re-evaluated by the controller both before scheduling the reveal and right
 * before it fires, so it should read live screen state (dialogs/sheets open, an active
 * snackbar, an in-flight upload, etc.) rather than a value captured once.
 */
@Composable
fun BoxScope.FirstPostFeedbackHost(
    surface: FeedbackSurface,
    isBlocked: () -> Boolean,
    viewModel: FirstPostFeedbackViewModel = hiltViewModel(),
) {
    val cardState by viewModel.cardState.collectAsState()
    val confirmationMessage by viewModel.confirmationMessage.collectAsState()
    val isTourActive by viewModel.isTourActive.collectAsState()

    LaunchedEffect(surface) {
        viewModel.onSurfaceReady(surface, isBlocked)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.cancelPendingShow() }
    }

    if (!isTourActive) cardState?.let { state ->
        // Every step but the notifications prompt swallows back (unchanged behavior). The
        // prompt (step 2.10, D11) is dismissible by design — back = "Maybe later".
        BackHandler(enabled = true) {
            if (state.step is FirstPostFeedbackStep.NotificationsPrompt) {
                viewModel.onNotificationsPromptDismissed()
            }
        }

        // "Yes, notify me" (step 0.4) — same request-vs-Settings routing as the fallback D card
        // ([com.revio.social.core.notifications.NotificationPrepromptHost]), driven by the flag
        // precomputed onto the step when it was entered.
        val context = LocalContext.current
        val notificationsPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            viewModel.onNotificationsPermissionResult(granted)
            viewModel.onNotificationsPromptAccepted()
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { /* consumat intenționat */ } }
                .clearAndSetSemantics { }
                .drawBehind { drawRect(OverlayScrim) },
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        ) {
            FirstPostFeedbackCard(
                state = state,
                onRatingSelected = viewModel::onRatingSelected,
                onReasonSelected = viewModel::onReasonSelected,
                onCommentChanged = viewModel::onCommentChanged,
                onSend = viewModel::onSend,
                onSkip = viewModel::onSkip,
                onNotNow = viewModel::onNotNow,
                onCloseX = viewModel::onCloseX,
                onNotificationsPromptAccepted = {
                    val step = state.step as? FirstPostFeedbackStep.NotificationsPrompt
                    when (resolveNotifyMeAction(Build.VERSION.SDK_INT, step?.permissionPreviouslyRequested ?: false)) {
                        NotificationPrepromptCtaAction.OPEN_SETTINGS -> {
                            viewModel.logNotificationsSettingsOpened()
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                            viewModel.onNotificationsPromptAccepted()
                        }
                        NotificationPrepromptCtaAction.REQUEST_PERMISSION ->
                            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onNotificationsPromptDismissed = viewModel::onNotificationsPromptDismissed,
            )
        }
    }

    confirmationMessage?.let { message ->
        LaunchedEffect(message) {
            delay(3000)
            viewModel.consumeConfirmation()
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        ) {
            CustomSnackbar(message = message, backgroundColor = SnackbarSuccessColor)
        }
    }
}
