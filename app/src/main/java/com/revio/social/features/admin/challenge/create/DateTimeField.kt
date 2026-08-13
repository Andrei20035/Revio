package com.revio.social.features.admin.challenge.create

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.social.core.ui.overlay.OverlayAccent
import com.revio.social.core.ui.overlay.OverlayBorder
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LabelColor = Color.White
private val HintColor = Color(0xFF8D8D8D)
private val ErrorColor = Color(0xFFF93939)

private val DateTimeDisplayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

fun LocalDateTime.toDisplayString(): String = format(DateTimeDisplayFormatter)

/** A combined date+time picker field: tapping opens [DatePickerDialog], then chains straight into
 * [TimePickerDialog] to complete the [LocalDateTime]. Same trigger mechanism as `BirthDateField`
 * (`ProfileCustomizationComponents.kt:695-713`) — a readOnly [OutlinedTextField] that opens on
 * [PressInteraction.Release] rather than `clickable`, so a scroll gesture starting on the field
 * doesn't also pop the dialog. */
@Composable
fun DateTimeField(
    label: String,
    value: LocalDateTime?,
    onValueChanged: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                val base = value ?: LocalDateTime.now()
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                onValueChanged(LocalDateTime.of(year, month + 1, dayOfMonth, hourOfDay, minute))
                            },
                            base.hour,
                            base.minute,
                            true,
                        ).show()
                    },
                    base.year,
                    base.monthValue - 1,
                    base.dayOfMonth,
                ).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = LabelColor,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(10.dp.actScaled()))
        OutlinedTextField(
            value = value?.toDisplayString() ?: "",
            onValueChange = {},
            interactionSource = interactionSource,
            readOnly = true,
            isError = isError,
            placeholder = { Text(text = "Select date & time", color = HintColor) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp.actScaled()),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White,
                cursorColor = OverlayAccent,
                focusedBorderColor = if (isError) ErrorColor else OverlayAccent,
                unfocusedBorderColor = if (isError) ErrorColor else OverlayBorder,
            ),
        )
    }
}
