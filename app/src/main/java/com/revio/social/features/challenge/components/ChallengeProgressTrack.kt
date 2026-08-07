package com.revio.social.features.challenge.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.revio.social.core.ui.scaling.scaled

internal val RefSegmentHeight = 6.dp
internal val RefSegmentGap = 6.dp
internal val RefSegmentCornerRadius = 3.dp

/** Discrete progress slots, one per required post — see the plan's §5 signature element. */
@Composable
internal fun SegmentedProgress(
    filledCount: Int,
    totalCount: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(RefSegmentGap.scaled()),
    ) {
        repeat(totalCount) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(RefSegmentHeight.scaled())
                    .clip(RoundedCornerShape(RefSegmentCornerRadius.scaled()))
                    .background(if (index < filledCount) accent else SegmentEmpty),
            )
        }
    }
}

/** Fallback for [SEGMENT_COUNT_THRESHOLD]+ required posts, where individual segments would be illegible. */
@Composable
internal fun ContinuousProgressBar(
    filledFraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val clamped = filledFraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RefSegmentHeight.scaled())
            .clip(RoundedCornerShape(RefSegmentCornerRadius.scaled()))
            .background(SegmentEmpty)
            .clearAndSetSemantics {},
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(RefSegmentHeight.scaled())
                .clip(RoundedCornerShape(RefSegmentCornerRadius.scaled()))
                .background(accent),
        )
    }
}
