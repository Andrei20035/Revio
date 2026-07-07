package com.revio.app.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun GradientText(
    text: String,
    fontWeight: FontWeight = FontWeight.Medium,
    fontSize: TextUnit = 50.sp,
    lineHeight: TextUnit = 40.sp,
    textAlign: TextAlign = TextAlign.Center
) {
    val gradientColors = listOf(
        Color(0xFF4A90E2),
        Color(0xFF9B59B6),
        Color(0xFFFF5F6D)
    )

    val stops = listOf(0.05f, 0.31f, 0.84f, 1f)

    Text(
        text = text,
        style = TextStyle(
            brush = Brush.linearGradient(
                colorStops = stops.zip(gradientColors).toTypedArray(),
            ),
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            textAlign = textAlign,
        )
    )
}