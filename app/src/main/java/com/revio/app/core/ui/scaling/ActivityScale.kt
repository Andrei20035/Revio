package com.revio.app.core.ui.scaling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

// Pixel 9 Pro (Figma baseline): 402dp screen width → scale == 1.0 exactly.
internal const val ActivityRefWidthDp = 402f
internal const val ActivityMinScale = 0.85f
internal const val ActivityMaxScale = 1.06f

// Text uses a gentler minimum so labels stay legible on narrow screens.
internal const val ActivityTextMinScale = 0.92f

/**
 * Provides the activity-screen scale factor to the composition tree.
 * Defaults to 1f so any composable outside a provider is unaffected.
 * Set by [rememberActivityScale] in ActivityScreen.
 */
val LocalActivityScale = compositionLocalOf { 1f }

/**
 * Computes the scale factor for the current screen width relative to the
 * 402dp Pixel 9 Pro baseline, clamped to [ActivityMinScale]..[ActivityMaxScale].
 */
@Composable
fun rememberActivityScale(): Float {
    val w = LocalConfiguration.current.screenWidthDp.toFloat()
    return (w / ActivityRefWidthDp).coerceIn(ActivityMinScale, ActivityMaxScale)
}

/** Scales this [Dp] by [LocalActivityScale]. Border/stroke/elevation values should NOT use this. */
@Composable
fun Dp.actScaled(): Dp = this * LocalActivityScale.current

/** Scales this [TextUnit] (sp) by [LocalActivityScale]. Preserves sp unit so fontScale is still honoured. */
@Composable
fun TextUnit.actScaled(): TextUnit = this * LocalActivityScale.current

/**
 * Like [actScaled] but clamps the scale at [ActivityTextMinScale] (0.92) instead of [ActivityMinScale] (0.85).
 * Use for font sizes so text stays legible even on the narrowest supported screens.
 */
@Composable
fun TextUnit.actScaledText(): TextUnit = this * LocalActivityScale.current.coerceAtLeast(ActivityTextMinScale)
