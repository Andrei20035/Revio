package com.revio.social.features.activity.components

import androidx.compose.runtime.Composable
import com.revio.social.core.ui.overlay.InfoOverlay

@Composable
fun TodayInteractionsInfoOverlay(
    onDismiss: () -> Unit,
) {
    InfoOverlay(
        title = "Today's Interactions",
        message = "This counts each person who liked or commented on your posts today, " +
            "once — not every individual like or comment. If the same person likes or " +
            "comments multiple times today, it still counts as just one interaction.",
        onDismiss = onDismiss,
    )
}
