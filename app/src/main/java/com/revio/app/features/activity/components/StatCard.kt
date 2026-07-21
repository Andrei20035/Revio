package com.revio.app.features.activity.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.app.core.ui.scaling.actScaled
import com.revio.app.core.ui.scaling.actScaledText
import com.revio.app.core.ui.theme.Poppins

private val CardFill = Color(0x613D3D3D)
private val CardBorder = Color(0xFF303030)
private val CardShape = RoundedCornerShape(12.dp)
private val WeeklyGreen = Color(0xFF28AB00)

@Composable
fun StatCard(
    title: String,
    value: Int,
    isWeeklyScore: Boolean,
    modifier: Modifier = Modifier,
    onInfoClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(109.dp.actScaled())
            .clip(CardShape)
            .border(1.dp, CardBorder, CardShape)
            .background(CardFill)
            .padding(16.dp.actScaled()),
        verticalArrangement = Arrangement.Center,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val titleFontSize = when {
                maxWidth < 135.dp -> 12.sp
                maxWidth < 150.dp -> 13.sp
                maxWidth < 165.dp -> 14.sp
                else -> 16.sp
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp.actScaled()),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = titleFontSize.actScaledText(),
                    maxLines = 1,
                    softWrap = false,
                )
                if (onInfoClick != null) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = Color.White,
                        modifier = Modifier
                            .size(16.dp.actScaled())
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onInfoClick,
                            ),
                    )
                }
            }
        }
        Text(
            text = if (isWeeklyScore) "+$value" else "$value",
            color = if (isWeeklyScore) WeeklyGreen else Color.White,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp.actScaledText(),
        )
    }
}
