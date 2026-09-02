package com.revio.social.core.notifications

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revio.social.core.ui.overlay.OverlayBorder
import com.revio.social.core.ui.overlay.OverlayScrim
import com.revio.social.core.ui.overlay.OverlaySurface
import com.revio.social.core.ui.theme.Poppins
import com.revio.social.core.ui.tour.rememberReducedMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/** What the card's "Notify me" CTA should do, resolved by [resolveNotifyMeAction]. */
internal enum class NotificationPrepromptCtaAction {
    /** Launch the OS `POST_NOTIFICATIONS` runtime-permission dialog (API 33+, never asked before). */
    REQUEST_PERMISSION,

    /** Send the user to Android's app-notification settings — the only real path to enabling
     * notifications once the OS dialog has already been shown once, and, on API 26-32 (no
     * `POST_NOTIFICATIONS` runtime permission to request), the only real path at all. */
    OPEN_SETTINGS,
}

/**
 * Pure decision for the "Notify me" CTA (step 0.3) — no Android permission dialog exists below
 * API 33, so on those versions the CTA must fall through to Settings instead of merely closing
 * the card without doing anything.
 */
internal fun resolveNotifyMeAction(sdkInt: Int, permissionPreviouslyRequested: Boolean): NotificationPrepromptCtaAction =
    when {
        permissionPreviouslyRequested -> NotificationPrepromptCtaAction.OPEN_SETTINGS
        sdkInt >= Build.VERSION_CODES.TIRAMISU -> NotificationPrepromptCtaAction.REQUEST_PERMISSION
        else -> NotificationPrepromptCtaAction.OPEN_SETTINGS
    }

private val CardCornerRadius = 20.dp
private val NotificationAccent = Color(0xFFF0AB25)
private val NotificationSecondaryText = Color(0xFF8D8D8D)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xB3FFFFFF)

/** Thin per-screen proxy over the singleton [NotificationPrepromptController], mirroring [com.revio.social.core.ui.feedback.FirstPostFeedbackViewModel]. */
@HiltViewModel
class NotificationPrepromptViewModel @Inject constructor(
    private val controller: NotificationPrepromptController,
) : ViewModel() {
    val uiState: StateFlow<NotificationPrepromptUiState> = controller.uiState
    fun onShown() = controller.onShown()
    fun onAccepted() = controller.onAccepted()
    fun onPermissionRequested(granted: Boolean) = controller.onPermissionRequested(granted)
    fun close() = controller.close()
    fun logSettingsOpened() = controller.logSettingsOpened()
    fun dismiss(reason: String = "dismissed") = controller.dismiss(reason)
    fun onResumed() = controller.onResumed()
}

/**
 * Hosts the fallback D notifications pre-prompt (step 2.11) — same visual language as
 * [com.revio.social.core.ui.feedback.FirstPostFeedbackHost]'s card (scrim + bottom-anchored card),
 * but standalone: shown from Activity, not chained off the first-post feedback flow.
 */
@Composable
fun BoxScope.NotificationPrepromptHost(
    viewModel: NotificationPrepromptViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    if (!uiState.visible) return

    // Records the show — only now, since the card has actually entered composition, is it fair
    // to count against the per-user cap/cooldown (step 0.2). Re-fires whenever the card re-enters
    // composition after having left it (the early return above), which is exactly "shown again";
    // [NotificationPrepromptController.onShown] is itself idempotent per pending show.
    LaunchedEffect(Unit) { viewModel.onShown() }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionRequested(granted)
        viewModel.close()
    }

    BackHandler(enabled = true) { viewModel.dismiss(reason = "back") }

    Box(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .clearAndSetSemantics { }
            .drawBehind { drawRect(OverlayScrim) },
    )

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 96.dp),
    ) {
        NotificationPrepromptCard(
            onNotifyMe = {
                viewModel.onAccepted()
                when (resolveNotifyMeAction(Build.VERSION.SDK_INT, uiState.permissionPreviouslyRequested)) {
                    NotificationPrepromptCtaAction.OPEN_SETTINGS -> {
                        viewModel.logSettingsOpened()
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                        viewModel.close()
                    }
                    NotificationPrepromptCtaAction.REQUEST_PERMISSION ->
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onNotNow = { viewModel.dismiss() },
        )
    }
}

@Composable
private fun NotificationPrepromptCard(
    onNotifyMe: () -> Unit,
    onNotNow: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val bellRotation = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reducedMotion) return@LaunchedEffect
        bellRotation.animateTo(12f, animationSpec = tween(120))
        bellRotation.animateTo(-10f, animationSpec = tween(130))
        bellRotation.animateTo(6f, animationSpec = tween(110))
        bellRotation.animateTo(-3f, animationSpec = tween(100))
        bellRotation.animateTo(0f, animationSpec = tween(100))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(CardCornerRadius))
            .clip(RoundedCornerShape(CardCornerRadius))
            .background(OverlaySurface)
            .border(1.dp, OverlayBorder, RoundedCornerShape(CardCornerRadius))
            .padding(20.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = "Notifications",
            tint = NotificationAccent,
            modifier = Modifier.graphicsLayer { rotationZ = bellRotation.value },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Someone just liked your spot",
            color = TextPrimary,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Want to know next time it happens?",
            color = TextSecondary,
            fontFamily = Poppins,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNotifyMe,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NotificationAccent),
        ) {
            Text("Notify me", fontFamily = Poppins, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
            Text("Not now", color = NotificationSecondaryText, fontFamily = Poppins, fontSize = 13.sp)
        }
    }
}
