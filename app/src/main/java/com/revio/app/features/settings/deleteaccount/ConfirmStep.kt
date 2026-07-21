package com.revio.app.features.settings.deleteaccount

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.app.core.ui.scaling.actScaled
import com.revio.app.core.ui.scaling.actScaledText
import com.revio.app.core.ui.theme.ProfileAccentGold
import com.revio.app.core.ui.theme.ProfileFieldErrorColor
import com.revio.app.data.model.AuthProvider
import com.revio.app.features.profile.components.LabeledTextField

private val LogoutRed = Color.Red

private val deletedItems = listOf(
    "Your profile and personal info",
    "All your posts and photos",
    "Your car listing",
    "Your likes and comments",
    "Your leaderboard rank and stats",
)

@Composable
fun ConfirmStep(
    uiState: DeleteAccountUiState,
    onAction: (DeleteAccountAction) -> Unit,
    onKeepAccount: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "This will permanently delete:",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp.actScaledText(),
        )

        Spacer(modifier = Modifier.height(16.dp.actScaled()))

        deletedItems.forEach { item ->
            Text(
                text = "•  $item",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp.actScaledText(),
                modifier = Modifier.padding(bottom = 8.dp.actScaled()),
            )
        }

        Spacer(modifier = Modifier.height(24.dp.actScaled()))

        if (uiState.provider == AuthProvider.GOOGLE) {
            LabeledTextField(
                label = "Type your username to confirm",
                value = uiState.usernameConfirmation,
                onValueChange = { onAction(DeleteAccountAction.UsernameConfirmationChanged(it)) },
                placeholderText = "Your username",
                isError = uiState.confirmFieldError != null,
            )
        } else {
            LabeledTextField(
                label = "Enter your password to confirm",
                value = uiState.password,
                onValueChange = { onAction(DeleteAccountAction.PasswordChanged(it)) },
                placeholderText = "Password",
                isError = uiState.confirmFieldError != null,
            )
        }

        if (uiState.confirmFieldError != null) {
            Spacer(modifier = Modifier.height(4.dp.actScaled()))
            Text(
                text = uiState.confirmFieldError,
                color = ProfileFieldErrorColor,
                fontSize = 13.sp.actScaledText(),
            )
        }

        if (uiState.generalError != null) {
            Spacer(modifier = Modifier.height(12.dp.actScaled()))
            Text(
                text = uiState.generalError,
                color = ProfileFieldErrorColor,
                fontSize = 13.sp.actScaledText(),
            )
        }

        Spacer(modifier = Modifier.height(28.dp.actScaled()))

        Button(
            onClick = onKeepAccount,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp.actScaled()),
            shape = RoundedCornerShape(33.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProfileAccentGold),
        ) {
            Text(
                text = "Keep my account",
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp.actScaledText(),
            )
        }

        Spacer(modifier = Modifier.height(12.dp.actScaled()))

        TextButton(
            onClick = { onAction(DeleteAccountAction.Confirm) },
            enabled = !uiState.isDeleting,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp.actScaled()),
        ) {
            if (uiState.isDeleting) {
                CircularProgressIndicator(
                    color = LogoutRed,
                    modifier = Modifier.size(22.dp.actScaled()),
                )
            } else {
                Text(
                    text = "Delete permanently",
                    color = LogoutRed,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp.actScaledText(),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp.actScaled()))
    }
}
