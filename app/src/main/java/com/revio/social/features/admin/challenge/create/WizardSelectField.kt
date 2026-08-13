package com.revio.social.features.admin.challenge.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText

private val FieldFill = Color(0x1FFFFFFF)
private val OverlaySurface = Color(0xFF11162E)

/** One (`id`, display label) pair for [WizardSelectOverlay] — `id` is what's reported back via
 * `onItemSelected`, so a brand can use its own name as the id while a family carries its UUID. */
data class WizardSelectOption(val id: String, val label: String)

/** A read-only select trigger — same shape as `DropdownFieldWithoutOverlay`
 * (`ProfileCustomizationComponents.kt:458`): a label above a field that opens [WizardSelectOverlay]
 * on tap. Styled dark to match the admin screens' navy background instead of that composable's
 * light profile-form fill. */
@Composable
fun WizardSelectField(
    label: String,
    selectedValue: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp.actScaledText(),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp.actScaled()),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(51.dp.actScaled())
                .clip(RoundedCornerShape(13.dp))
                .background(FieldFill.copy(alpha = if (enabled) 0.12f else 0.06f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp.actScaled()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedValue ?: placeholder,
                color = if (selectedValue != null) Color.White else Color.White.copy(alpha = 0.4f),
                fontSize = 14.sp.actScaledText(),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (enabled) 0.7f else 0.35f),
            )
        }
    }
}

/** Same structure as `DropdownOverlay` (`ProfileCustomizationComponents.kt:514`) — a full-screen
 * dimming scrim with a centered, scrollable options card — styled dark for the admin wizard. */
@Composable
fun WizardSelectOverlay(
    visible: Boolean,
    items: List<WizardSelectOption>,
    onItemSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val itemHeight = 52.dp
    val maxVisibleItems = 6
    val listHeight = itemHeight * items.size.coerceIn(1, maxVisibleItems)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(200, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(150)) +
            scaleOut(targetScale = 0.92f, animationSpec = tween(150, easing = FastOutLinearInEasing)),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = listHeight)
                    .align(Alignment.Center)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    ),
                colors = CardDefaults.cardColors(containerColor = OverlaySurface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                    items(items, key = { it.id }) { option ->
                        WizardSelectOverlayItem(option = option, onClick = { onItemSelected(option.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardSelectOverlayItem(option: WizardSelectOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp.actScaled(), vertical = 14.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = option.label,
            color = Color.White,
            fontSize = 15.sp.actScaledText(),
        )
    }
}
