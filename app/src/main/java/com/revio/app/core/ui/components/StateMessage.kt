package com.revio.app.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val StateMessageAccent = Color(0xFF34D7C4)

@Composable
fun StateMessage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    accentColor: Color = StateMessageAccent,
    verticalPadding: Dp = 48.dp,
    horizontalPadding: Dp = 20.dp,
    titleColor: Color = Color.White,
    subtitleColor: Color = Color.White.copy(alpha = 0.7f),
    titleFontSize: TextUnit = 18.sp,
    subtitleFontSize: TextUnit = 14.sp,
    titleSubtitleSpacing: Dp = 6.dp,
    actionSpacing: Dp = 12.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding, horizontal = horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = titleColor, fontSize = titleFontSize, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(titleSubtitleSpacing))
            Text(subtitle, color = subtitleColor, fontSize = subtitleFontSize)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(actionSpacing))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = accentColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
