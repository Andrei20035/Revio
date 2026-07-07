package com.revio.app.core.ui.scaling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

// Pixel 9 Pro (Figma baseline): 402dp screen width → scale == 1.0 exactly.
internal const val ProfileDashRefWidthDp = 402f
internal const val ProfileDashMinScale = 0.85f
internal const val ProfileDashMaxScale = 1.06f

// Text uses a gentler minimum so labels stay legible on narrow screens.
internal const val ProfileDashTextMinScale = 0.92f

// Vertical spacing: 402dp is both baseline and maximum → scale ≤ 1.0.
// Wider screens stay at 1.0; only narrower screens compress.
internal const val ProfileDashVSpacingMinScale = 0.88f
internal const val ProfileDashVSpacingMaxScale = 1.00f

/**
 * Provides the profile-dashboard scale factor to the composition tree.
 * Defaults to 1f so any composable outside a provider is unaffected.
 * Set by [rememberProfileDashScale] in ProfileDashboardScreen.
 */
val LocalProfileDashScale = compositionLocalOf { 1f }

/**
 * Provides the vertical-spacing scale factor for ProfileDashboardScreen.
 * Clamped to [ProfileDashVSpacingMinScale]..[ProfileDashVSpacingMaxScale] so 402dp is the visual maximum.
 * Defaults to 1f outside a provider.
 */
val LocalProfileDashVSpacingScale = compositionLocalOf { 1f }

/**
 * Computes the scale factor for the current screen width relative to the
 * 402dp Pixel 9 Pro baseline, clamped to [ProfileDashMinScale]..[ProfileDashMaxScale].
 */
@Composable
fun rememberProfileDashScale(): Float {
    val w = LocalConfiguration.current.screenWidthDp.toFloat()
    return (w / ProfileDashRefWidthDp).coerceIn(ProfileDashMinScale, ProfileDashMaxScale)
}

/**
 * Computes the vertical-spacing scale for ProfileDashboardScreen,
 * clamped to [ProfileDashVSpacingMinScale]..[ProfileDashVSpacingMaxScale].
 */
@Composable
fun rememberProfileDashVSpacingScale(): Float {
    val w = LocalConfiguration.current.screenWidthDp.toFloat()
    return (w / ProfileDashRefWidthDp).coerceIn(ProfileDashVSpacingMinScale, ProfileDashVSpacingMaxScale)
}

/** Scales this [Dp] by [LocalProfileDashScale]. Border/stroke/elevation values should NOT use this. */
@Composable
fun Dp.dashScaled(): Dp = this * LocalProfileDashScale.current

/** Scales this [Dp] by [LocalProfileDashVSpacingScale]. Use only for vertical spacing in ProfileDashboardScreen. */
@Composable
fun Dp.dashScaledV(): Dp = this * LocalProfileDashVSpacingScale.current

/** Scales this [TextUnit] (sp) by [LocalProfileDashScale]. Preserves sp unit so fontScale is still honoured. */
@Composable
fun TextUnit.dashScaled(): TextUnit = this * LocalProfileDashScale.current

/**
 * Like [dashScaled] but clamps the scale at [ProfileDashTextMinScale] (0.92) instead of [ProfileDashMinScale] (0.85).
 * Use for font sizes so text stays legible even on the narrowest supported screens.
 */
@Composable
fun TextUnit.dashScaledText(): TextUnit = this * LocalProfileDashScale.current.coerceAtLeast(ProfileDashTextMinScale)
