package com.revio.social.core.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Configures the current [androidx.compose.ui.window.Dialog] window to use a flat, uniform dim
 * scrim behind it, identical on every device — no blur-behind, no OS version branching.
 *
 * Must be called from within a [androidx.compose.ui.window.Dialog] composable content.
 */
@Composable
fun DimOnlyDialogWindow(dimAmount: Float = 0.5f) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window

    LaunchedEffect(dialogWindow, dimAmount) {
        dialogWindow?.setDimAmount(dimAmount)
    }
}
