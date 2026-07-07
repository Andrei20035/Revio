package com.revio.app.features.feed.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revio.app.core.ui.components.shimmer
import com.revio.app.core.ui.scaling.scaled

// Reference dimensions — must mirror FeedPostCard exactly (Pixel 9 Pro baseline, scale == 1.0).
private val RefAvatarSize = 37.dp
private val RefAvatarUsernameSpacing = 8.dp
private val RefSkeletonUsernameHeight = 13.dp
private val RefSkeletonUsernameWidth = 96.dp
private val RefSkeletonHeaderRowSpacing = 6.dp
private val RefSkeletonLocationHeight = 11.dp
private val RefSkeletonLocationWidth = 150.dp
private val RefHeaderTrailingSpacing = 8.dp
private val RefHeaderBottomSpacing = 9.dp
private val RefImageCornerRadius = 18.dp
private val RefImageBottomSpacing = 14.dp
private val RefEngagementIndent = 12.dp
private val RefSkeletonEngagementHeight = 12.dp
private val RefEngagementBottomSpacing = 12.dp
private val RefCaptionIndent = 12.dp
private val RefSkeletonCaptionHeight = 12.dp

/**
 * Visual-only loading placeholder that mirrors the structure and spacing of a real feed post
 * (see `FeedPostCard`): avatar + username/location, the large image card, the like/comment row,
 * and a caption line — each filled with a [shimmer]. Purely presentational; holds no data.
 *
 * Spacing/shape values intentionally match the real card so the layout doesn't shift when actual
 * posts replace the skeletons.
 */
@Composable
fun FeedPostSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ---- Header: avatar · username/location · options ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(RefAvatarSize.scaled()).shimmer(CircleShape))
            Spacer(modifier = Modifier.width(RefAvatarUsernameSpacing.scaled()))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.height(RefSkeletonUsernameHeight.scaled()).width(RefSkeletonUsernameWidth.scaled()).shimmer())
                Spacer(modifier = Modifier.height(RefSkeletonHeaderRowSpacing.scaled()))
                Box(modifier = Modifier.height(RefSkeletonLocationHeight.scaled()).width(RefSkeletonLocationWidth.scaled()).shimmer())
            }
            Spacer(modifier = Modifier.width(RefHeaderTrailingSpacing.scaled()))
        }

        Spacer(modifier = Modifier.height(RefHeaderBottomSpacing.scaled()))

        // ---- Main image card (same 375×468 aspect + 18dp radius as the real post) ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(375f / 468f)
                .shimmer(RoundedCornerShape(RefImageCornerRadius.scaled()))
        )

        Spacer(modifier = Modifier.height(RefImageBottomSpacing.scaled()))

        // ---- Engagement row (like icon + count · comment icon + count), indented like the real one ----
        Row(
            modifier = Modifier.padding(start = RefEngagementIndent.scaled()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(RefSkeletonEngagementHeight.scaled())
                    .fillMaxWidth(0.4f)
                    .shimmer()
            )
        }

        Spacer(modifier = Modifier.height(RefEngagementBottomSpacing.scaled()))

        // ---- Caption line ----
        Box(
            modifier = Modifier
                .padding(start = RefCaptionIndent.scaled())
                .height(RefSkeletonCaptionHeight.scaled())
                .fillMaxWidth(0.6f)
                .shimmer()
        )
    }
}
