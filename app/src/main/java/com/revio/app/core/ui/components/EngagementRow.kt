package com.revio.app.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.revio.app.R
import java.util.Locale

/**
 * Like icon. Shows `like_selected` when liked, `like` otherwise. On a like (false→true) it plays a
 * subtle pop — a quick scale-up that springs back — for premium tactile feedback. Unliking does not
 * animate, and the initial liked state on first composition is not animated. The tap is handled by
 * the caller.
 */
@Composable
fun LikeIcon(
    liked: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(liked) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        if (liked) {
            scale.animateTo(1.22f, animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing))
            scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium))
        }
    }

    Image(
        painter = painterResource(if (liked) R.drawable.like_selected else R.drawable.like),
        contentDescription = if (liked) "Unlike" else "Like",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}

/**
 * Adaptive width for a count slot, sized to the widest string the count can format to in each
 * magnitude band (matching [formatCount]). Small counts get a compact slot — no large empty gap —
 * while larger counts expand in controlled steps so the layout never visibly jumps on a 0↔1 change.
 */
fun interactionCountWidth(count: Long, scale: Float): Dp = when {
    count < 10 -> 16.dp * scale       // "0".."9"
    count < 100 -> 24.dp * scale      // "10".."99"
    count < 1_000 -> 32.dp * scale    // "100".."999"
    count < 10_000 -> 40.dp * scale   // "1K".."9.9K"
    count < 100_000 -> 48.dp * scale  // "10K".."99.9K"
    else -> 56.dp * scale             // "100K"+, "1M"+
}

/** Compact engagement count: 1200 → "1.2K", 1_000_000 → "1M", 341 → "341". */
fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> trimZero(value / 1_000_000.0) + "M"
    value >= 1_000 -> trimZero(value / 1_000.0) + "K"
    else -> value.toString()
}

private fun trimZero(v: Double): String {
    val s = String.format(Locale.US, "%.1f", v)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}
