package com.revio.social.features.settings.feedback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.theme.ProfileAccentGold

private val SubtitleColor = Color(0xFF8D8D8D)

@Composable
fun FeedbackSentStep(
    onDone: () -> Unit,
    onSendAnother: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp.actScaled()))
        Text(
            text = "Thanks for helping us improve Revio.",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp.actScaledText(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp.actScaled()))
        Text(
            text = "Your feedback has been sent successfully.",
            color = SubtitleColor,
            fontSize = 14.sp.actScaledText(),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp.actScaled()))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp.actScaled()),
            shape = RoundedCornerShape(33.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProfileAccentGold),
        ) {
            Text(
                text = "Done",
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp.actScaledText(),
            )
        }

        Spacer(modifier = Modifier.height(12.dp.actScaled()))

        TextButton(onClick = onSendAnother) {
            Text(text = "Send another feedback", color = SubtitleColor, fontSize = 14.sp.actScaledText())
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF030310)
@Composable
private fun FeedbackSentStepPreview() {
    FeedbackSentStep(onDone = {}, onSendAnother = {})
}
