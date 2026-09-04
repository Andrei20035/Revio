package com.revio.social.features.challenge.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.scaling.scaled
import com.revio.social.core.ui.scaling.scaledText
import com.revio.social.features.challenge.ChallengeCta

private val CtaTextColor = Color(0xFF00161F)
private const val CtaDisabledBackgroundAlpha = 0.35f
private const val CtaDisabledTextAlpha = 0.6f

private val CtaHeight = 48.dp
private val CtaCornerRadius = 14.dp
private val CtaFontSize = 15.sp

/**
 * The challenge CTA button shared by [ChallengeCard] and the Challenge Detail screen — one visual
 * treatment for [ChallengeCta], so the two surfaces render identically for the same state (see
 * the plan's §5/§6 pas 5). When [cta] is disabled, the button stays in place but dims, drops its
 * click, and reports itself as disabled to accessibility — no ripple, no `onClick`.
 */
@Composable
fun ChallengeCtaButton(
    cta: ChallengeCta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CtaHeight.scaled())
            .clip(RoundedCornerShape(CtaCornerRadius.scaled()))
            .background(if (cta.enabled) ChallengeAccent else ChallengeAccent.copy(alpha = CtaDisabledBackgroundAlpha))
            .clickable(
                enabled = cta.enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Start a camera spot",
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = cta.label
                if (!cta.enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cta.label,
            color = if (cta.enabled) CtaTextColor else CtaTextColor.copy(alpha = CtaDisabledTextAlpha),
            fontSize = CtaFontSize.scaledText(),
            fontWeight = FontWeight.SemiBold,
        )
    }
}
