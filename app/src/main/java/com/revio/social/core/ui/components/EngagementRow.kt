package com.revio.social.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import com.revio.social.R
import java.util.Locale

// Like-pop tuning. Liking is the emphatic gesture — deeper dip, bigger overshoot; unliking is
// deliberately quieter. See [LikeIcon].
private const val LIKE_DIP_SCALE = 0.80f
private const val LIKE_OVERSHOOT_SCALE = 1.20f
private const val UNLIKE_DIP_SCALE = 0.88f
private const val UNLIKE_OVERSHOOT_SCALE = 1.08f

/**
 * Like icon. Shows `like_selected` when liked, `like` otherwise. Both liking and unliking play an
 * Instagram-style pop: the heart dips briefly (as if pressed), overshoots past its resting size,
 * then springs back to rest. Unliking uses a shallower dip and a smaller overshoot so removing a
 * like reads as less emphatic than giving one. The initial liked state on first composition is not
 * animated. The tap is handled by the caller.
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
        // Dip → overshoot → spring settle. The dip is what gives the tap its "pressed" feel; going
        // straight to the overshoot (the previous behaviour) reads as a bounce rather than a press.
        val dip = if (liked) LIKE_DIP_SCALE else UNLIKE_DIP_SCALE
        val overshoot = if (liked) LIKE_OVERSHOOT_SCALE else UNLIKE_OVERSHOOT_SCALE
        scale.animateTo(dip, animationSpec = tween(durationMillis = 90, easing = FastOutLinearInEasing))
        scale.animateTo(overshoot, animationSpec = tween(durationMillis = 130, easing = LinearOutSlowInEasing))
        scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
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
