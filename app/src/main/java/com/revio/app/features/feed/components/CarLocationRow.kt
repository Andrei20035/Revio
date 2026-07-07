package com.revio.app.features.feed.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.revio.app.R
import com.revio.app.core.ui.theme.Poppins
import com.revio.app.core.ui.scaling.scaled

// Same dark/glass palette as the post-options dropdown, so the details popover matches it.
private val PopoverSurface = Color(0xFF1B1F33)
private val PopoverBorder = Color(0x1FFFFFFF) // white @ ~12%

// Reference dimensions (Pixel 9 Pro baseline, scale == 1.0).
private val RefCarIconSize = 14.dp
private val RefGpsIconSize = 15.dp
private val RefRowTextSize = 13.3.sp
// Width of the right-edge alpha fade applied only when the row overflows.
private val RefFadeWidth = 24.dp
private val RefCarIconTextSpacing = 5.dp
private val RefGpsLeadingSpacing = 6.dp
private val RefGpsIconTextSpacing = 5.dp
private val RefPopoverOffsetY = 22.dp
private val RefPopoverMaxWidth = 280.dp
private val RefPopoverCornerRadius = 16.dp
private val RefPopoverPaddingH = 14.dp
private val RefPopoverPaddingV = 12.dp
private val RefPopoverItemSpacing = 10.dp
private val RefPopoverCarIconSize = 16.dp
private val RefPopoverGpsIconSize = 17.dp
private val RefPopoverIconTextSpacing = 10.dp
private val RefPopoverFontSize = 13.3.sp

/**
 * The post header's second row — `ic_car · car name · ic_gps · location` — kept on a single line.
 *
 * Overflow handling:
 *  - The car name takes priority: it's measured greedily, while the location gets the leftover
 *    space (and therefore truncates first).
 *  - When the row can't fit, a subtle right-edge alpha fade masks the overflow instead of a hard
 *    cut. The fade is applied *only* while something actually overflows — never when it all fits.
 *
 * Tapping the row opens a compact details popover (full car name + full location), styled like the
 * post-options dropdown and dismissed by tapping outside. Layout/visual style of the row itself is
 * unchanged.
 */
@Composable
fun CarLocationRow(
    carName: String,
    location: String?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Per-text overflow flags, fed by onTextLayout. Reset when the underlying text changes.
    var carOverflow by remember(carName) { mutableStateOf(false) }
    var locationOverflow by remember(location) { mutableStateOf(false) }
    val showFade = carOverflow || locationOverflow
    val popoverOffsetY = with(LocalDensity.current) { RefPopoverOffsetY.scaled().roundToPx() }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = true },
                )
                .rightEdgeFade(visible = showFade, fadeWidth = RefFadeWidth.scaled()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_car),
                contentDescription = null,
                modifier = Modifier.size(RefCarIconSize.scaled()),
            )
            Spacer(modifier = Modifier.width(RefCarIconTextSpacing.scaled()))
            // No weight → measured greedily, so the car name keeps priority over the location.
            Text(
                // Design: "Porsche 911," — trailing comma when a location follows.
                text = if (location != null) "$carName," else carName,
                color = Color.White,
                fontSize = RefRowTextSize.scaled(),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                onTextLayout = { carOverflow = it.hasVisualOverflow },
            )
            // Location resolved from the post's coordinates (town, country). Hidden when the backend
            // hasn't geocoded the post yet — never fabricated.
            if (location != null) {
                Spacer(modifier = Modifier.width(RefGpsLeadingSpacing.scaled()))
                Image(
                    painter = painterResource(R.drawable.ic_gps),
                    contentDescription = null,
                    modifier = Modifier.size(RefGpsIconSize.scaled()),
                )
                Spacer(modifier = Modifier.width(RefGpsIconTextSpacing.scaled()))
                Text(
                    text = location,
                    color = Color.White,
                    fontSize = RefRowTextSize.scaled(),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    onTextLayout = { locationOverflow = it.hasVisualOverflow },
                    // Weighted → takes only the leftover space and truncates before the car name.
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, popoverOffsetY),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                CarDetailsPopover(carName = carName, location = location)
            }
        }
    }
}

/** Compact full-details popover: full car name + full location, in the post-options glass style. */
@Composable
private fun CarDetailsPopover(carName: String, location: String?) {
    val cornerRadius = RefPopoverCornerRadius.scaled()
    Column(
        modifier = Modifier
            .widthIn(max = RefPopoverMaxWidth.scaled())
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(PopoverSurface)
            .border(1.dp, PopoverBorder, RoundedCornerShape(cornerRadius))
            .padding(horizontal = RefPopoverPaddingH.scaled(), vertical = RefPopoverPaddingV.scaled()),
        verticalArrangement = Arrangement.spacedBy(RefPopoverItemSpacing.scaled()),
    ) {
        DetailRow(iconRes = R.drawable.ic_car, iconSize = RefPopoverCarIconSize.scaled(), text = carName)
        if (location != null) {
            DetailRow(iconRes = R.drawable.ic_gps, iconSize = RefPopoverGpsIconSize.scaled(), text = location)
        }
    }
}

@Composable
private fun DetailRow(iconRes: Int, iconSize: Dp, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
        )
        Spacer(modifier = Modifier.width(RefPopoverIconTextSpacing.scaled()))
        // Full, untruncated text — wraps within the compact max width for very long values.
        Text(
            text = text,
            color = Color.White,
            fontFamily = Poppins,
            fontSize = RefPopoverFontSize.scaled(),
        )
    }
}

/**
 * Masks the right edge with a horizontal alpha gradient (content fades to transparent) — but only
 * when [visible]. Uses an offscreen layer + [BlendMode.DstIn] so the fade affects this content only.
 * A no-op when nothing overflows, so the row never shows a phantom fade while it fits.
 */
private fun Modifier.rightEdgeFade(visible: Boolean, fadeWidth: Dp): Modifier {
    if (!visible) return this
    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fadePx = fadeWidth.toPx()
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - fadePx,
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}
