package com.revio.social.features.admin.challenge.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText

private val StepActiveColor = Color(0xFF34D7C4)
private val StepInactiveColor = Color(0x26FFFFFF)

private val WizardStep.label: String
    get() = when (this) {
        WizardStep.Vehicle -> "Vehicle"
        WizardStep.GoalReward -> "Goal & reward"
        WizardStep.ScheduleReview -> "Schedule & review"
    }

/** Three segments, filled up to (and including) the current step — "Step N of 3 · <name>" below. */
@Composable
fun WizardStepIndicator(step: WizardStep, modifier: Modifier = Modifier) {
    val steps = WizardStep.entries
    val currentIndex = steps.indexOf(step)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp.actScaled()),
        ) {
            steps.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp.actScaled())
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index <= currentIndex) StepActiveColor else StepInactiveColor),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp.actScaled()))
        Text(
            text = "Step ${currentIndex + 1} of ${steps.size} · ${step.label}",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp.actScaledText(),
        )
    }
}
