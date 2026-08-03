package com.revio.social.core.ui.feedback

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
        BackHandler(enabled = true) { }

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
