package com.revio.social.features.feed.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.revio.social.core.ui.tour.rememberReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Heart burst tuning. See the "Timeline-ul exact al animației" table in the double-tap-to-like
// plan for the rationale behind these numbers.
private const val START_SCALE = 0.40f
private const val APPEAR_OVERSHOOT_SCALE = 1.15f
private const val SETTLE_SCALE = 1.00f
private const val EXIT_SCALE = 0.86f

private const val WOBBLE_LEFT_DEGREES = -14f
private const val WOBBLE_RIGHT_DEGREES = 10f
private const val REST_ROTATION_DEGREES = 0f

private const val APPEAR_DURATION_MS = 170
private const val FADE_IN_DURATION_MS = 90
private const val SETTLE_DURATION_MS = 90
private const val WOBBLE_LEFT_DURATION_MS = 65
private const val WOBBLE_RIGHT_DURATION_MS = 75
private const val HOLD_DURATION_MS = 300L
private const val EXIT_DURATION_MS = 240

// Reduced-motion fallback: no scale/rotation, just a plain fade in/out.
private const val REDUCED_MOTION_FADE_IN_MS = 1
private const val REDUCED_MOTION_HOLD_MS = 500L
private const val REDUCED_MOTION_FADE_OUT_MS = 150

private val HeartSize = 120.dp
private val HeartColor = Color(0xFFFF2D55)

/**
 * TikTok-style heart burst shown in the center of a post image on double-tap. Purely visual —
 * consumes no pointer input and is invisible to accessibility services — driven entirely by
 * [trigger]: incrementing it (even mid-animation) restarts the whole sequence from scratch, so
 * rapid repeated double-taps each get a fresh burst. A [trigger] of 0 (the initial value) never
 * plays anything.
 */
@Composable
fun DoubleTapLikeBurst(trigger: Int, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(START_SCALE) }
    val rotation = remember { Animatable(REST_ROTATION_DEGREES) }
    val alpha = remember { Animatable(0f) }
    val reducedMotion = rememberReducedMotion()

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect

        // Reset instantly so a new trigger always starts from the same clean state, even if the
        // previous burst's animation was cancelled mid-flight.
        scale.snapTo(START_SCALE)
        rotation.snapTo(REST_ROTATION_DEGREES)
        alpha.snapTo(0f)

        if (reducedMotion) {
            scale.snapTo(SETTLE_SCALE)
            alpha.animateTo(1f, animationSpec = tween(REDUCED_MOTION_FADE_IN_MS, easing = LinearEasing))
            delay(REDUCED_MOTION_HOLD_MS)
            alpha.animateTo(0f, animationSpec = tween(REDUCED_MOTION_FADE_OUT_MS, easing = LinearEasing))
            return@LaunchedEffect
        }

        // Appear + fade in, in parallel.
        coroutineScope {
            launch { alpha.animateTo(1f, animationSpec = tween(FADE_IN_DURATION_MS, easing = LinearEasing)) }
            launch { scale.animateTo(APPEAR_OVERSHOOT_SCALE, animationSpec = tween(APPEAR_DURATION_MS, easing = LinearOutSlowInEasing)) }
        }

        // Settle to resting scale.
        scale.animateTo(SETTLE_SCALE, animationSpec = tween(SETTLE_DURATION_MS, easing = FastOutSlowInEasing))

        // Left-right rotation wobble, then spring back to rest.
        rotation.animateTo(WOBBLE_LEFT_DEGREES, animationSpec = tween(WOBBLE_LEFT_DURATION_MS, easing = LinearOutSlowInEasing))
        rotation.animateTo(WOBBLE_RIGHT_DEGREES, animationSpec = tween(WOBBLE_RIGHT_DURATION_MS, easing = FastOutSlowInEasing))
        rotation.animateTo(
            REST_ROTATION_DEGREES,
            animationSpec = spring(dampingRatio = 0.40f, stiffness = Spring.StiffnessMedium),
        )

        // Hold, then fade out + shrink slightly, in parallel.
        delay(HOLD_DURATION_MS)
        coroutineScope {
            launch { alpha.animateTo(0f, animationSpec = tween(EXIT_DURATION_MS, easing = FastOutLinearInEasing)) }
            launch { scale.animateTo(EXIT_SCALE, animationSpec = tween(EXIT_DURATION_MS, easing = FastOutSlowInEasing)) }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (alpha.value > 0f) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = HeartColor,
                modifier = Modifier
                    .size(HeartSize)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        rotationZ = rotation.value
                        this.alpha = alpha.value
                    },
            )
        }
    }
}
