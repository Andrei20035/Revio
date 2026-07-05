package com.example.carspotter.core.ui.components

import android.graphics.BlurMaskFilter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.carspotter.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Tabs that own a selectable nav slot. The center "+" is intentionally not a tab. */
enum class FeedNavItem { Home, Leaderboard, Activity, Profile }

// Figma baseline: 362dp pill inside a 402dp screen frame.
private const val ReferenceWidthDp = 402f
private const val MinScale = 0.85f
private const val MaxScale = 1.15f

// Reference dimensions at 1.0× (Pixel 9 Pro, 402dp screen width).
private val RefSideMargin = 20.dp
private val RefNavBarHeight = 60.dp
private val RefInnerPadding = 27.dp
private val RefCornerRadius = 32.dp
private val RefIconSize = 32.dp
private val RefPlusSize = 46.dp
private val RefShadowBlur = 30.dp
private val RefShadowOffsetY = (-8).dp

/**
 * Custom floating bottom navigation matching the Figma `feed` design.
 *
 * Not built on Material3 [androidx.compose.material3.NavigationBar] — it's a hand-rolled
 * glassy pill (362×64, fully rounded) with four icon tabs and an elevated center "+" button.
 *
 * Selection is fully driven by [selected] (state is hoisted to the caller), so it survives
 * recomposition and configuration changes. Active tabs swap to their bolder `*_selected`
 * vector; the Profile tab shows the user's avatar with a white ring when active.
 *
 * All internal dimensions scale linearly with the screen width relative to the 402dp Figma
 * baseline, clamped to [MinScale, MaxScale] so the bar stays proportional on every device
 * while Pixel 9 Pro (402dp) always renders at exactly 1.0×.
 */
@Composable
fun FloatingBottomNav(
    selected: FeedNavItem,
    profilePictureUrl: String?,
    onHome: () -> Unit,
    onLeaderboard: () -> Unit,
    onPlus: () -> Unit,
    onActivity: () -> Unit,
    onProfile: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val scale = (LocalConfiguration.current.screenWidthDp / ReferenceWidthDp).coerceIn(MinScale, MaxScale)
    val cornerRadius = RefCornerRadius * scale
    val navBarShape = RoundedCornerShape(cornerRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Pill side margins: Figma 362dp pill within the 402dp frame → ~20dp each side.
            .padding(horizontal = RefSideMargin * scale)
            // Figma drop shadow: #000000 @25%, X 0 / Y -8 (upward), blur 30, spread 0.
            // Drawn behind (unclipped) so it doesn't affect the measured size or layout.
            .glassDropShadow(
                cornerRadius = cornerRadius,
                color = Color.Black.copy(alpha = 0.25f),
                blur = RefShadowBlur * scale,
                offsetX = 0.dp,
                offsetY = RefShadowOffsetY * scale,
            )
            .clip(navBarShape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurRadius = 30.dp
                        tints = listOf(
                            HazeTint(Color(0xFF00161F).copy(alpha = 0.25f)),
                        )
                        noiseFactor = 0.12f
                    }
                } else {
                    Modifier.background(NavBarFill)
                }
            )
            .border(width = 1.dp, color = NavBarBorder, shape = navBarShape)
            .height(RefNavBarHeight * scale)
            // Consume every pointer event over the pill's bounds so taps on gaps/background
            // don't fall through to whatever is rendered behind this foreground overlay.
            // Children (icons) still get first crack at Main-pass events, so their clicks work.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            // Inner padding tuned so the five slots land on the Figma icon centers.
            .padding(horizontal = RefInnerPadding * scale),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavIcon(
            res = if (selected == FeedNavItem.Home) R.drawable.home_button_selected else R.drawable.home_button,
            contentDescription = "Home",
            size = RefIconSize * scale,
            onClick = onHome,
        )
        NavIcon(
            res = if (selected == FeedNavItem.Leaderboard) R.drawable.leaderboard_selected else R.drawable.leaderboard,
            contentDescription = "Leaderboard",
            size = RefIconSize * scale,
            onClick = onLeaderboard,
        )
        PlusButton(size = RefPlusSize * scale, onClick = onPlus)
        NavIcon(
            res = if (selected == FeedNavItem.Activity) R.drawable.activity_selected else R.drawable.activity,
            contentDescription = "Activity",
            size = (RefIconSize - 2.dp) * scale,
            onClick = onActivity,
        )
        ProfileTab(
            profilePictureUrl = profilePictureUrl,
            selected = selected == FeedNavItem.Profile,
            size = RefIconSize * scale,
            onClick = onProfile,
        )
    }
}

@Composable
private fun NavIcon(
    @DrawableRes res: Int,
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(res),
            contentDescription = contentDescription,
            // Fit keeps each glyph's intrinsic aspect ratio (e.g. the 21×27 flame) inside the slot.
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
        )
    }
}

/**
 * Center create-post button. Always identical — no selected state, no icon swap — and
 * visually elevated via its larger size and own drop shadow.
 */
@Composable
private fun PlusButton(size: Dp, onClick: () -> Unit) {
    Image(
        painter = painterResource(R.drawable.plus_button),
        contentDescription = "Post your find",
        modifier = Modifier
            .size(size)
            .shadow(elevation = 12.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

@Composable
private fun ProfileTab(
    profilePictureUrl: String?,
    selected: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    val base = Modifier
        .size(size)
        .clip(CircleShape)
        .then(
            if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier
        )
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    if (profilePictureUrl.isNullOrBlank()) {
        Image(
            painter = painterResource(R.drawable.profile_picture),
            contentDescription = "Profile",
            contentScale = ContentScale.Crop,
            modifier = base,
        )
    } else {
        AsyncImage(
            model = profilePictureUrl,
            contentDescription = "Profile",
            contentScale = ContentScale.Crop,
            modifier = base,
            placeholder = painterResource(R.drawable.profile_picture),
            fallback = painterResource(R.drawable.profile_picture),
            error = painterResource(R.drawable.profile_picture),
        )
    }
}

// Glassy dark-teal fill. Figma layers a near-transparent fill over a 30px *backdrop* blur.
// Compose has no clean first-party backdrop blur, so the production-safe equivalent is a
// translucent dark fill: we keep the sampled teal tint (darker at the edges, brighter in the
// middle) but drop its alpha so the dark feed behind reads through for a frosted-glass look.
private const val NavBarFillAlpha = 0.72f
private val NavBarFill = Brush.horizontalGradient(
    listOf(
        Color(0xFF00161F).copy(alpha = NavBarFillAlpha),
        Color(0xFF002F3C).copy(alpha = NavBarFillAlpha),
        Color(0xFF00161F).copy(alpha = NavBarFillAlpha),
    )
)

private val NavBarBorder = Color.White.copy(alpha = 0.12f)

/**
 * Draws a soft, colored drop shadow behind the composable, matching Figma's shadow controls
 * (color, X/Y offset, blur). Unlike [androidx.compose.ui.draw.shadow] this supports an arbitrary
 * color and an upward (negative Y) offset. It's a pure draw operation — no layout/size impact —
 * and is rendered before the clip so the soft halo can extend beyond the pill bounds.
 */
private fun Modifier.glassDropShadow(
    cornerRadius: Dp,
    color: Color,
    blur: Dp,
    offsetX: Dp,
    offsetY: Dp,
): Modifier = drawBehind {
    val frameworkPaint = Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        val blurPx = blur.toPx()
        if (blurPx > 0f) maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    }
    val r = cornerRadius.toPx()
    val dx = offsetX.toPx()
    val dy = offsetY.toPx()
    drawContext.canvas.nativeCanvas.drawRoundRect(
        dx,
        dy,
        size.width + dx,
        size.height + dy,
        r,
        r,
        frameworkPaint,
    )
}
