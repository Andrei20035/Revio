package com.revio.social.features.admin.challenge.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.revio.social.core.ui.overlay.OverlayAccent
import com.revio.social.core.ui.overlay.OverlayBorder
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText

private val LabelColor = Color.White
private val HintColor = Color(0xFF8D8D8D)
private val ErrorColor = Color(0xFFF93939)

/** A dark-themed text field for the wizard — same visual shape as `FeedbackFormStep`'s private
 * `FormMessageField` (`:373`), generalized with a keyboard type/IME action and an optional hard
 * [maxLength] (used for the title, which the server column caps at 150 chars). Numeric fields are
 * deliberately *not* filtered beyond [KeyboardType.Number]'s soft-keyboard hint — non-numeric
 * input must still reach the field so [validateGoalStep]'s "Enter a whole number." error path is
 * reachable, not dead code. */
@Composable
fun WizardTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    maxLength: Int? = null,
    isError: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = LabelColor,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp.actScaledText(),
        )
        Spacer(modifier = Modifier.height(10.dp.actScaled()))
        OutlinedTextField(
            value = value,
            onValueChange = { newValue -> onValueChange(if (maxLength != null) newValue.take(maxLength) else newValue) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = placeholder, color = HintColor) },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = OverlayAccent,
                focusedBorderColor = if (isError) ErrorColor else OverlayAccent,
                unfocusedBorderColor = if (isError) ErrorColor else OverlayBorder,
            ),
        )
    }
}

/** Inline field error, shown below a [WizardTextField] — same shape as `PersonalInfoScreen`'s
 * private `FieldWarning` (`:739`), simplified to error-only since the wizard never needs the
 * neutral-hint variant. */
@Composable
fun FieldWarning(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = ErrorColor,
        fontSize = 12.sp.actScaledText(),
        modifier = modifier.padding(top = 4.dp.actScaled(), start = 4.dp.actScaled()),
    )
}
