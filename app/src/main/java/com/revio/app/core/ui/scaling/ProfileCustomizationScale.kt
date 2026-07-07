package com.revio.app.core.ui.scaling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

// Pixel 9 Pro (Figma baseline): 402dp screen width → scale == 1.0 exactly.
internal const val ProfileRefWidthDp = 402f
internal const val ProfileMinScale = 0.85f
internal const val ProfileMaxScale = 1.10f

// Text uses a gentler minimum so labels and field values stay legible on narrow screens.
internal const val ProfileTextMinScale = 0.92f

// Vertical spacing: Pixel 9 Pro is baseline AND maximum → scale ≤ 1.0.
// Wider screens stay at 1.0; only narrower screens compress vertical gaps.
internal const val ProfileVSpacingMinScale = 0.88f
internal const val ProfileVSpacingMaxScale = 1.00f

/**
 * Provides the profile-customization scale factor to the composition tree.
 * Defaults to 1f so any composable outside a provider is unaffected.
 * Set by [rememberProfileScale] in PersonalInfoStep / CarInfoStep.
 */
val LocalProfileScale = compositionLocalOf { 1f }

/**
 * Provides the vertical-spacing scale factor for ProfileCustomization forms.
 * Clamped to [ProfileVSpacingMinScale]..[ProfileVSpacingMaxScale] so Pixel 9 Pro
 * is the visual maximum for vertical gaps and field spacing.
 * Defaults to 1f outside a provider.
 */
val LocalProfileVSpacingScale = compositionLocalOf { 1f }

/**
 * Computes the scale factor for the current screen width relative to the
 * 402dp Pixel 9 Pro baseline, clamped to [ProfileMinScale]..[ProfileMaxScale].
 */
@Composable
fun rememberProfileScale(): Float {
    val w = LocalConfiguration.current.screenWidthDp.toFloat()
    return (w / ProfileRefWidthDp).coerceIn(ProfileMinScale, ProfileMaxScale)
}

/**
 * Computes the vertical-spacing scale for ProfileCustomization forms,
 * clamped to [ProfileVSpacingMinScale]..[ProfileVSpacingMaxScale].
 * Pixel 9 Pro (402dp) maps to exactly 1.0; wider screens stay at 1.0.
 */
@Composable
fun rememberProfileVSpacingScale(): Float {
    val w = LocalConfiguration.current.screenWidthDp.toFloat()
    return (w / ProfileRefWidthDp).coerceIn(ProfileVSpacingMinScale, ProfileVSpacingMaxScale)
}

/** Scales this [Dp] by [LocalProfileScale]. Border/stroke/elevation values should NOT use this. */
@Composable
fun Dp.profileScaled(): Dp = this * LocalProfileScale.current

/** Scales this [Dp] by [LocalProfileVSpacingScale]. Use only for vertical spacing in profile forms. */
@Composable
fun Dp.profileScaledV(): Dp = this * LocalProfileVSpacingScale.current

/** Scales this [TextUnit] (sp) by [LocalProfileScale]. Preserves sp unit so fontScale is still honoured. */
@Composable
fun TextUnit.profileScaled(): TextUnit = this * LocalProfileScale.current

/**
 * Like [profileScaled] but clamps the scale at [ProfileTextMinScale] (0.92) instead of
 * [ProfileMinScale] (0.85). Use for font sizes so text stays legible on the narrowest screens.
 */
@Composable
fun TextUnit.profileScaledText(): TextUnit = this * LocalProfileScale.current.coerceAtLeast(ProfileTextMinScale)
