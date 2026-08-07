package com.revio.social.features.challenge.components

import androidx.compose.ui.graphics.Color
import com.revio.social.core.ui.theme.ProfileAccentGold

// Same value as FeedScreen's private FeedAccent — kept as its own token here since that one
// isn't visible outside FeedScreen.kt. ProfileAccentGold is reused as-is: the single place in the
// app gold appears, reserved for the completed-challenge state.
internal val ChallengeAccent = Color(0xFF34D7C4)
internal val ChallengeGold = ProfileAccentGold
internal val CardBackground = Color.White.copy(alpha = 0.06f)
internal val CardBorder = Color.White.copy(alpha = 0.10f)
internal val SegmentEmpty = Color.White.copy(alpha = 0.14f)

/** Above this many required posts, discrete segments stop being legible; fall back to a bar. */
internal const val SEGMENT_COUNT_THRESHOLD = 8

/** Below this many hours left, the eyebrow's time portion switches to [ChallengeGold] for urgency. */
internal const val URGENT_THRESHOLD_HOURS = 6L
