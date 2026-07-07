package com.revio.app.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.revio.app.R

/**
 * Poppins Medium — used by the feed for engagement counts and post captions,
 * matching the Figma `feed` design (Poppins:Medium, 14sp). Bundled as a static
 * font so it renders offline without a downloadable-font round-trip.
 *
 * The rest of the feed (username / car / location) uses [FontFamily.Default],
 * which is Roboto on Android and matches the design's Roboto styles.
 */
val Poppins = FontFamily(
    Font(R.font.poppins_medium, FontWeight.Medium),
)
