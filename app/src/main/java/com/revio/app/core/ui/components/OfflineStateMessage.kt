package com.revio.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Offline-specific variant of [StateMessage]: a crossed-out Wi-Fi icon plus user-friendly copy,
 * shown when a request fails because the device has no network connection.
 */
@Composable
fun OfflineStateMessage(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    title: String = "You're not connected to the internet",
    subtitle: String = "Check your Wi-Fi or mobile data. We'll refresh automatically once you're back online.",
    actionLabel: String = "Try again",
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WifiOffIcon()
        Spacer(modifier = Modifier.height(12.dp))
        StateMessage(
            title = title,
            subtitle = subtitle,
            actionLabel = if (onRetry != null) actionLabel else null,
            onAction = onRetry,
            verticalPadding = 0.dp,
        )
    }
}

@Composable
private fun WifiOffIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.7f),
    iconSize: Dp = 40.dp,
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val strokeWidth = size.minDimension * 0.08f
        val dotRadius = size.minDimension * 0.06f
        val center = Offset(size.width / 2f, size.height * 0.78f)

        drawCircle(color = color, radius = dotRadius, center = center)

        val maxRadius = size.minDimension * 0.42f
        for (step in 1..3) {
            val radius = maxRadius * step / 3f
            drawArc(
                color = color,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        drawLine(
            color = color,
            start = Offset(size.width * 0.12f, size.height * 0.12f),
            end = Offset(size.width * 0.88f, size.height * 0.88f),
            strokeWidth = strokeWidth * 1.1f,
            cap = StrokeCap.Round,
        )
    }
}
