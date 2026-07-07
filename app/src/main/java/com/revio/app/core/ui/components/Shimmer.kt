package com.revio.app.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// Dark, neutral skeleton tones tuned to the CarSpotter feed (bg ≈ #05071B). The highlight is
// only a touch lighter than the base so the sweep reads as a subtle sheen, never a bright/white
// flash.
private val ShimmerBase = Color(0xFF161B2E)
private val ShimmerHighlight = Color(0xFF252D45)

/**
 * Paints a subtle shimmer placeholder fill clipped to [shape]: a soft highlight band that sweeps
 * horizontally across an otherwise flat dark base. Reusable on any sized box to build skeletons.
 *
 * Self-contained (own [rememberInfiniteTransition]); placeholders composed in the same frame stay
 * in phase, so a list of skeletons shimmers together.
 */
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(6.dp)): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, delayMillis = 250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )

    clip(shape).drawBehind {
        val width = size.width
        if (width <= 0f) {
            drawRect(ShimmerBase)
            return@drawBehind
        }
        // Width of the moving highlight band, and its leading-edge x as it travels off-left to off-right.
        val band = width * 0.7f
        val x = progress * (width + band) - band
        val brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to ShimmerBase,
                0.5f to ShimmerHighlight,
                1f to ShimmerBase,
            ),
            start = Offset(x, 0f),
            end = Offset(x + band, 0f),
        )
        drawRect(brush)
    }
}
