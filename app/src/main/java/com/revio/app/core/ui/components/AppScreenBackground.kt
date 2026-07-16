package com.revio.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AppBackground = Color(0xFF030310)

/**
 * Dark navy background shared across all main app screens (Feed, Leaderboard, etc.).
 * Provides a top scrim (black→transparent) and a bottom scrim (transparent→black)
 * to frame content and make the floating nav bar feel grounded.
 *
 * Layering, from back to front:
 *  1. base navy background
 *  2. [content] — the scrollable feed/list
 *  3. bottom fade scrim — fades [content] out near the bottom edge
 *  4. [foreground] — the floating nav bar and any overlays (snackbars). Rendered
 *     ABOVE the bottom scrim so the nav is never darkened/tinted by the fade.
 *
 * The nav must live in [foreground], not in [content]; otherwise the bottom scrim
 * would draw on top of it.
 *
 * [showBottomScrim] lets screens without a floating nav (e.g. plain forms) opt
 * out of the bottom fade, since there's nothing for it to ground.
 */
@Composable
fun AppScreenBackground(
    modifier: Modifier = Modifier,
    showBottomScrim: Boolean = true,
    foreground: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        // Top scrim — black fading into the navy surface.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarTop + 120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)))
        )

        content()

        // Bottom fade — fades the content out near the bottom edge. Drawn after
        // content() but BEFORE foreground(), so it never covers the nav bar.
        if (showBottomScrim) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(146.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            )
        }

        // Foreground — floating nav + overlays. Always above the fade.
        foreground()
    }
}
