package com.revio.app.features.feed.components

import android.os.Build
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.revio.app.R
import com.revio.app.core.ui.scaling.LocalFeedScale
import com.revio.app.core.ui.scaling.rememberFeedScale
import com.revio.app.core.ui.scaling.scaled

// Reference dimensions (Pixel 9 Pro baseline, scale == 1.0).
private val RefBlurRadius = 28.dp
private val RefButtonSpacing = 26.dp
private val RefCircleOptionSize = 130.dp

/**
 * "Post your find" modal overlay (Figma `post-your-find`): the current screen is blurred/dimmed and
 * two large white circular buttons — camera and gallery — float centered over it.
 *
 * Implemented as a [Dialog] so it floats above the current screen without navigating. The backdrop
 * uses a real OS-level blur-behind on Android 12+ (`FLAG_BLUR_BEHIND`); on older devices, or when
 * cross-window blur is disabled, it falls back to a stronger dim so the effect stays close to Figma.
 *
 * Dismisses on back press, on tapping the dimmed area outside the buttons, or after a selection
 * (the caller is expected to close it from [onCamera]/[onGallery]).
 */
@Composable
fun PostYourFindOverlay(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // full-screen overlay
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        // Dialog creates a new Android window (new composition) — re-derive scale here.
        CompositionLocalProvider(LocalFeedScale provides rememberFeedScale()) {
            val context = LocalContext.current
            val blurRadiusPx = with(LocalDensity.current) { RefBlurRadius.scaled().roundToPx() }
            val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window

            // Configure the dialog window: blur-behind where supported, otherwise a dim scrim.
            LaunchedEffect(dialogWindow) {
                val window = dialogWindow ?: return@LaunchedEffect
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blurEnabled = context.getSystemService(WindowManager::class.java)
                        ?.isCrossWindowBlurEnabled == true
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.apply { blurBehindRadius = blurRadiusPx }
                    // Light dim when real blur is doing the work (matches Figma's ~22% scrim);
                    // heavier dim as the fallback when blur isn't available.
                    window.setDimAmount(if (blurEnabled) 0.25f else 0.5f)
                } else {
                    window.setDimAmount(0.5f)
                }
            }

            // Tapping the blurred backdrop (anywhere but the buttons) dismisses.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(RefButtonSpacing.scaled()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleOption(
                        res = R.drawable.post_with_camera,
                        contentDescription = "Post with camera",
                        onClick = onCamera,
                    )
                    CircleOption(
                        res = R.drawable.post_from_gallery,
                        contentDescription = "Post from gallery",
                        onClick = onGallery,
                    )
                }
            }
        }
    }
}

/** A single large circular option button (the PNG already bakes in the white circle + icon). */
@Composable
private fun CircleOption(
    @DrawableRes res: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Image(
        painter = painterResource(res),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(RefCircleOptionSize.scaled())
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}
