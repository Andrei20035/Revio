package com.revio.social.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.revio.social.core.navigation.Screen
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText

private val CardBg = Color(0x3DD9D9D9)
private val ItemTextColor = Color.White

/** Moderation section of the admin panel: reports queue and user lookup. */
@Composable
fun AdminModerationScreen(navController: NavController) {
    AppScreenBackgroundWithTopBar(
        title = "Moderation",
        onBack = { navController.popBackStack() },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.layout.Column {
                AdminModerationRow(
                    icon = Icons.Outlined.Report,
                    label = "Reports queue",
                    topRound = true,
                    bottomRound = false,
                    onClick = { navController.navigate(Screen.AdminReports.route) },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f)),
                )
                AdminModerationRow(
                    icon = Icons.Outlined.Person,
                    label = "Find user",
                    topRound = false,
                    bottomRound = true,
                    onClick = { navController.navigate(Screen.AdminUser.route) },
                )
            }
        }
    }
}

@Composable
private fun AdminModerationRow(
    icon: ImageVector,
    label: String,
    topRound: Boolean,
    bottomRound: Boolean,
    onClick: () -> Unit,
) {
    val topRadius = if (topRound) 20.dp else 0.dp
    val bottomRadius = if (bottomRound) 20.dp else 0.dp
    val shape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp.actScaled())
            .clip(shape)
            .background(CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ItemTextColor,
            modifier = Modifier.size(24.dp.actScaled()),
        )
        Spacer(modifier = Modifier.width(16.dp.actScaled()))
        Text(
            text = label,
            color = ItemTextColor,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp.actScaledText(),
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp.actScaled()),
        )
    }
}
