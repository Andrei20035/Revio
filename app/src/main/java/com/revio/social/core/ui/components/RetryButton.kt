package com.revio.social.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.revio.social.R
import com.revio.social.core.ui.tour.rememberReducedMotion

private const val ONE_SHOT_MS = 600
private const val SPIN_MS = 800
private const val STOP_MS = 400
private const val STEP_DEG = 30f
private val STEPS_PER_REVOLUTION = (360f / STEP_DEG).toInt()
private val STEP_MS = SPIN_MS / STEPS_PER_REVOLUTION

/**
 * The feed footer's retry control: a [R.drawable.retry] icon that spins to communicate what's
 * happening, plus an optional label. [onClick] is invoked on every tap regardless of [spinning]
 * — offline-vs-online request gating and de-duplication live in the caller, not here.
 *
 * - [spinning] `false`: a tap plays exactly one 360° revolution, fast-out/slow-in, then stops
 *   upright. A tap mid-revolution restarts the animation from 0° rather than queuing another.
 * - [spinning] `true`: the icon rotates continuously at a constant angular velocity (no seam at
 *   the 360°/0° wrap, unlike repeating an eased revolution would produce) until [spinning] goes
 *   back to `false`, at which point it finishes with a single decelerating revolution into the
 *   upright position rather than cutting off mid-turn.
 */
@Composable
fun RetryButton(
    onClick: () -> Unit,
    spinning: Boolean,
    modifier: Modifier = Modifier,
    label: String? = "Retry",
    tint: Color = StateMessageAccent,
    iconSize: Dp = 22.dp,
) {
    val rotation by rememberRetrySpin(spinning)

    Row(
        modifier = modifier.clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.retry),
            contentDescription = label ?: "Retry",
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { rotationZ = rotation },
        )
        if (label != null) {
            Text(text = label, color = tint, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Drives [RetryButton]'s icon rotation (degrees, 0..360) as a function of [spinning]. Extracted
 * from [RetryButton] so a test can assert the actual animation curve — the seam-free continuous
 * spin and the clean deceleration into a stop — instead of only the icon's static end state.
 *
 * - [spinning] `false`: a `true`->`false` blip (e.g. a footer retry tap that fails the offline
 *   pre-flight check almost immediately) plays exactly one 360° revolution, fast-out/slow-in,
 *   then stops upright. A blip mid-revolution restarts the animation from 0° rather than
 *   queuing another.
 * - [spinning] `true`: the icon rotates continuously at a constant angular velocity (no seam at
 *   the 360°/0° wrap, unlike repeating an eased revolution would produce) until [spinning] goes
 *   back to `false`, at which point it finishes with a single decelerating revolution into the
 *   upright position rather than cutting off mid-turn.
 */
@Composable
fun rememberRetrySpin(spinning: Boolean): State<Float> {
    val rotation = remember { Animatable(0f) }
    var spinToken by remember { mutableIntStateOf(0) }
    val spinningNow by rememberUpdatedState(spinning)
    val reducedMotion = rememberReducedMotion()

    // `spinning` turning true is a start trigger, exactly like a tap.
    LaunchedEffect(spinning) {
        if (spinning) spinToken++
    }

    // Keyed on `spinToken` only — NOT on `spinning` — so a mid-spin `spinning` flip to false
    // doesn't restart this effect and cancel the in-flight stop sequence below.
    LaunchedEffect(spinToken) {
        if (spinToken == 0) return@LaunchedEffect
        if (reducedMotion) {
            rotation.snapTo(0f)
            return@LaunchedEffect
        }

        rotation.snapTo(0f)

        if (!spinningNow) {
            // Offline one-shot: exactly one revolution, decelerating into the stop.
            rotation.animateTo(360f, tween(ONE_SHOT_MS, easing = FastOutSlowInEasing))
            rotation.snapTo(0f)
            return@LaunchedEffect
        }

        // Continuous rotation while loading, advanced in small equal-duration steps so the loop
        // can exit promptly when `spinningNow` flips and so every step shares the same angular
        // velocity as the last — a repeating full-revolution tween would instead decelerate then
        // re-accelerate at every 360°/0° wrap.
        var base = 0f
        while (spinningNow) {
            val next = base + STEP_DEG
            rotation.animateTo(next, tween(STEP_MS, easing = LinearEasing))
            base = if (next >= 360f) {
                rotation.snapTo(0f)
                0f
            } else {
                next
            }
        }

        // Clean stop: decelerate from wherever the loop left off to the next multiple of 360.
        rotation.animateTo(360f, tween(STOP_MS, easing = FastOutSlowInEasing))
        rotation.snapTo(0f)
    }

    return rotation.asState()
}
