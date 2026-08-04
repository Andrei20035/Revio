package com.revio.social.features.settings.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lightbulb
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.overlay.OverlaySurface
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.data.model.FeedbackCategory

private val CardTitleColor = Color.White
private val CardSubtitleColor = Color(0xFF8D8D8D)

private data class CategoryOption(
    val category: FeedbackCategory,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
)

private val categoryOptions = listOf(
    CategoryOption(
        category = FeedbackCategory.NOT_WORKING,
        title = "Something isn't working",
        subtitle = "Report a bug or unexpected behavior.",
        icon = Icons.Outlined.BugReport,
        iconTint = Color(0xFFF4978E),
    ),
    CategoryOption(
        category = FeedbackCategory.CONFUSING,
        title = "Something is confusing",
        subtitle = "Tell us what was difficult to understand or use.",
        icon = Icons.Outlined.HelpOutline,
        iconTint = Color(0xFFFFD166),
    ),
    CategoryOption(
        category = FeedbackCategory.FEATURE_IDEA,
        title = "Feature idea",
        subtitle = "Suggest something you'd like Revio to do.",
        icon = Icons.Outlined.Lightbulb,
        iconTint = Color(0xFF6EE7B7),
    ),
    CategoryOption(
        category = FeedbackCategory.GENERAL,
        title = "General feedback",
        subtitle = "Share any other thoughts about the app.",
        icon = Icons.Outlined.ChatBubbleOutline,
        iconTint = Color(0xFFA5B4FC),
    ),
)

@Composable
fun CategoryPickerStep(
    onAction: (FeedbackAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Help us make Revio better. Tell us what isn't working, what feels " +
                "confusing, or what you'd like to see next.",
            color = CardSubtitleColor,
            fontSize = 14.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(4.dp.actScaled()))
        Text(
            text = "Your feedback directly influences what we improve next.",
            color = CardSubtitleColor,
            fontSize = 14.sp.actScaledText(),
        )

        Spacer(modifier = Modifier.height(24.dp.actScaled()))

        categoryOptions.forEachIndexed { index, option ->
            CategoryCard(
                title = option.title,
                subtitle = option.subtitle,
                icon = option.icon,
                iconTint = option.iconTint,
                onClick = { onAction(FeedbackAction.SelectCategory(option.category)) },
            )
            if (index != categoryOptions.lastIndex) {
                Spacer(modifier = Modifier.height(12.dp.actScaled()))
            }
        }

        Spacer(modifier = Modifier.height(24.dp.actScaled()))
    }
}

@Composable
private fun CategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(OverlaySurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(16.dp.actScaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp.actScaled())
                .clip(RoundedCornerShape(14.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp.actScaled()),
            )
        }
        Spacer(modifier = Modifier.width(16.dp.actScaled()))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = CardTitleColor,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp.actScaledText(),
            )
            Spacer(modifier = Modifier.height(4.dp.actScaled()))
            Text(
                text = subtitle,
                color = CardSubtitleColor,
                fontSize = 13.sp.actScaledText(),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF030310)
@Composable
private fun CategoryPickerStepPreview() {
    CategoryPickerStep(onAction = {})
}
