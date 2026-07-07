package com.revio.app.core.ui.scaling

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

// Pixel 9 Pro (Figma baseline): 402dp screen width → scale == 1.0 exactly.
internal const val FeedReferenceWidthDp = 402f
internal const val FeedMinScale = 0.88f
internal const val FeedMaxScale = 1.06f

// Text uses a gentler minimum so labels stay legible on narrow screens.
internal const val FeedTextMinScale = 0.95f

// Vertical spacing in FeedPostCard: Pixel 9 Pro is both baseline AND maximum → scale ≤ 1.0.
// Wider screens (e.g. OnePlus 8T, 411dp) stay at 1.0; only narrower screens compress.
internal const val FeedVSpacingMinScale = 0.90f
internal const val FeedVSpacingMaxScale = 1.00f

/**
 * Provides the feed-level scale factor to the composition tree.
 * Defaults to 1f so any composable outside a provider is unaffected.
 * Set by [rememberFeedScale] in FeedScreen.
 */
val LocalFeedScale = compositionLocalOf { 1f }

/**
 * Provides the vertical-spacing scale factor for FeedPostCard.
 * Clamped to [FeedVSpacingMinScale]..[FeedVSpacingMaxScale] so Pixel 9 Pro is the visual maximum.
 * Defaults to 1f outside a provider.
 */
val LocalFeedVSpacingScale = compositionLocalOf { 1f }

/**
 * Computes the scale factor for the current screen width relative to the
 * 402dp Pixel 9 Pro baseline, clamped to [FeedMinScale]..[FeedMaxScale].
 * FloatingBottomNav has its own independent scaling (0.85–1.15) and is not affected.
 */
@Composable
fun rememberFeedScale(): Float {
    val w = LocalConfiguration.current.screenWidthDp.toFloat()
    return (w / FeedReferenceWidthDp).coerceIn(FeedMinScale, FeedMaxScale)
}

/**
 * Computes the vertical-spacing scale for FeedPostCard, clamped to [FeedVSpacingMinScale]..[FeedVSpacingMaxScale].
 * Pixel 9 Pro (402dp) maps to exactly 1.0; wider screens stay at 1.0.
 */
@Composable
fun rememberFeedVSpacingScale(): Float {
    val w = LocalConfiguration.current.screenWidthDp.toFloat()
    return (w / FeedReferenceWidthDp).coerceIn(FeedVSpacingMinScale, FeedVSpacingMaxScale)
}

/** Scales this [Dp] by [LocalFeedScale]. Border/stroke/elevation values should NOT use this. */
@Composable
fun Dp.scaled(): Dp = this * LocalFeedScale.current

/** Scales this [Dp] by [LocalFeedVSpacingScale]. Use only for vertical spacing in FeedPostCard. */
@Composable
fun Dp.scaledV(): Dp = this * LocalFeedVSpacingScale.current

/** Scales this [TextUnit] (sp) by [LocalFeedScale]. Preserves sp unit so fontScale is still honoured. */
@Composable
fun TextUnit.scaled(): TextUnit = this * LocalFeedScale.current

/**
 * Like [scaled] but clamps the scale at [FeedTextMinScale] (0.95) instead of [FeedMinScale] (0.88).
 * Use for font sizes so text stays legible even on the narrowest supported screens.
 */
@Composable
fun TextUnit.scaledText(): TextUnit = this * LocalFeedScale.current.coerceAtLeast(FeedTextMinScale)
