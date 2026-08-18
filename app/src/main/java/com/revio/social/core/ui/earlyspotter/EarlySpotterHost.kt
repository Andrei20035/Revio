package com.revio.social.core.ui.earlyspotter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revio.social.core.earlyspotter.EarlySpotterCardState
import com.revio.social.core.ui.overlay.OverlayScrim

/**
 * Hosts the one-time, combined Early Spotter card, anchored above the floating nav bar the
 * same way [com.revio.social.core.ui.feedback.FirstPostFeedbackHost] is. Meant to be composed
 * inside `AppScreenBackground`'s `foreground` slot (a [BoxScope]) alongside the nav bar.
 */
@Composable
fun BoxScope.EarlySpotterHost(
    viewModel: EarlySpotterHostViewModel = hiltViewModel(),
) {
    val state by viewModel.earlySpotterController.state.collectAsState()

    if (state == EarlySpotterCardState.Hidden) return

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
        EarlySpotterCard(
            state = state,
            onDismiss = { viewModel.onDismissed() },
        )
    }
}
