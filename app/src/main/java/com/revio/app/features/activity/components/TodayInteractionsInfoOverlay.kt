package com.revio.app.features.activity.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.revio.app.core.ui.theme.Poppins

private val OverlayMenuSurface = Color(0xFF1B1F33)
private val OverlayMenuBorder = Color(0x1FFFFFFF)

@Composable
fun TodayInteractionsInfoOverlay(
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        val context = LocalContext.current
        val blurRadiusPx = with(LocalDensity.current) { 28.dp.roundToPx() }
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window

        LaunchedEffect(dialogWindow) {
            val window = dialogWindow ?: return@LaunchedEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurEnabled = context.getSystemService(WindowManager::class.java)
                    ?.isCrossWindowBlurEnabled == true
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = blurRadiusPx }
                window.setDimAmount(if (blurEnabled) 0.25f else 0.5f)
            } else {
                window.setDimAmount(0.5f)
            }
        }

        // Outer full-screen box: tapping outside the card dismisses.
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
            // Inner card: consumes clicks so they don't reach the dismiss handler.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(OverlayMenuSurface)
                    .border(1.dp, OverlayMenuBorder, RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Today's Interactions",
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "This counts each person who liked or commented on your posts today, " +
                        "once — not every individual like or comment. If the same person likes or " +
                        "comments multiple times today, it still counts as just one interaction.",
                    color = Color.White.copy(alpha = 0.65f),
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
