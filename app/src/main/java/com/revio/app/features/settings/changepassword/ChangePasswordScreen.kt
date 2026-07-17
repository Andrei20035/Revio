package com.revio.app.features.settings.changepassword

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.revio.app.R
import com.revio.app.core.ui.components.AppScreenBackground
import com.revio.app.core.ui.scaling.LocalActivityScale
import com.revio.app.core.ui.scaling.actScaled
import com.revio.app.core.ui.scaling.actScaledText
import com.revio.app.core.ui.scaling.rememberActivityScale
import com.revio.app.core.ui.theme.Poppins
import com.revio.app.core.ui.theme.ProfileAccentGold
import com.revio.app.core.ui.theme.ProfileFieldErrorColor
import com.revio.app.core.ui.theme.ProfileFieldFocused
import com.revio.app.core.ui.theme.ProfileFieldHintColor
import com.revio.app.core.ui.theme.ProfileFieldNeutral

@Composable
fun ChangePasswordScreen(
    navController: NavController,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            Toast.makeText(context, "Password changed", Toast.LENGTH_SHORT).show()
            viewModel.onSaveSuccessConsumed()
            navController.popBackStack()
        }
    }

    AppScreenBackground(showBottomScrim = false) {
        CompositionLocalProvider(LocalActivityScale provides rememberActivityScale()) {
            val focusManager = LocalFocusManager.current
            val newPasswordFocusRequester = remember { FocusRequester() }
            val confirmPasswordFocusRequester = remember { FocusRequester() }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp.actScaled()),
            ) {
                // ── Top bar ──────────────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp.actScaled()),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp.actScaled()))
                    Text(
                        text = "Change password",
                        color = Color.White,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Medium,
                        fontSize = 25.sp.actScaledText(),
                    )
                }

                // ── Fields ───────────────────────────────────────────────
                Spacer(modifier = Modifier.height(28.dp.actScaled()))

                PasswordEntryField(
                    label = "Current password",
                    value = uiState.oldPassword,
                    onValueChange = viewModel::onOldPasswordChanged,
                    isVisible = uiState.isOldVisible,
                    onToggleVisibility = viewModel::onToggleOldVisibility,
                    isError = uiState.oldPasswordError != null,
                    enabled = !uiState.isSaving,
                    imeAction = ImeAction.Next,
                    onImeAction = { newPasswordFocusRequester.requestFocus() },
                )
                uiState.oldPasswordError?.let { FieldWarning(text = it, isError = true) }
                    ?: Spacer(Modifier.height(23.dp.actScaled()))

                val newError = uiState.newPasswordError
                    ?: if (uiState.newPassword.isNotEmpty() && !newPasswordMeetsRequirements(uiState.newPassword)) {
                        "8+ characters with uppercase, lowercase, number, and symbol."
                    } else {
                        null
                    }
                PasswordEntryField(
                    label = "New password",
                    value = uiState.newPassword,
                    onValueChange = viewModel::onNewPasswordChanged,
                    isVisible = uiState.isNewVisible,
                    onToggleVisibility = viewModel::onToggleNewVisibility,
                    isError = newError != null,
                    enabled = !uiState.isSaving,
                    imeAction = ImeAction.Next,
                    onImeAction = { confirmPasswordFocusRequester.requestFocus() },
                    modifier = Modifier.focusRequester(newPasswordFocusRequester),
                )
                newError?.let { FieldWarning(text = it, isError = true) }
                    ?: Spacer(Modifier.height(23.dp.actScaled()))

                val confirmError = uiState.confirmPasswordError
                    ?: if (uiState.confirmPassword.isNotEmpty() && uiState.confirmPassword != uiState.newPassword) {
                        "Passwords don't match"
                    } else {
                        null
                    }
                PasswordEntryField(
                    label = "Confirm new password",
                    value = uiState.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChanged,
                    isVisible = uiState.isConfirmVisible,
                    onToggleVisibility = viewModel::onToggleConfirmVisibility,
                    isError = confirmError != null,
                    enabled = !uiState.isSaving,
                    imeAction = ImeAction.Done,
                    onImeAction = { focusManager.clearFocus() },
                    modifier = Modifier.focusRequester(confirmPasswordFocusRequester),
                )
                confirmError?.let { FieldWarning(text = it, isError = true) }
                    ?: Spacer(Modifier.height(23.dp.actScaled()))

                uiState.generalError?.let {
                    Text(
                        text = it,
                        color = ProfileFieldErrorColor,
                        fontSize = 13.sp.actScaledText(),
                        modifier = Modifier.padding(bottom = 12.dp.actScaled()),
                    )
                }

                // ── Save ─────────────────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Button(
                    onClick = { showConfirmDialog = true },
                    enabled = !uiState.isSaveBlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp.actScaled()),
                    shape = RoundedCornerShape(33.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF0AB25),
                        disabledContainerColor = Color(0xFFF0AB25).copy(alpha = 0.7f),
                    ),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(22.dp.actScaled()),
                        )
                    } else {
                        Text(
                            text = "Save",
                            color = Color.Black,
                            fontSize = 18.sp.actScaledText(),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp.actScaled()))
            }
        }
    }

    if (showConfirmDialog) {
        ChangePasswordConfirmDialog(
            onConfirm = {
                showConfirmDialog = false
                viewModel.onSave()
            },
            onDismiss = { showConfirmDialog = false },
        )
    }
}

@Composable
private fun ChangePasswordConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change password?") },
        text = {
            Text(
                "Are you sure you want to change your password? You'll stay signed in on this " +
                    "device, but you'll be signed out everywhere else.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Change password") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PasswordEntryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    isError: Boolean,
    enabled: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        color = ProfileFieldNeutral,
        fontSize = 14.5.sp.actScaledText(),
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, start = 8.dp),
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        isError = isError,
        singleLine = true,
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() },
        ),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    painter = painterResource(
                        id = if (isVisible) R.drawable.visibility_off_24px else R.drawable.visibility_24px,
                    ),
                    contentDescription = if (isVisible) "Hide password" else "Show password",
                    modifier = Modifier.size(24.dp.actScaled()),
                    tint = Color(0xFF434343),
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp.actScaled()),
        shape = RoundedCornerShape(13.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ProfileAccentGold,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = ProfileFieldErrorColor,
            focusedContainerColor = ProfileFieldFocused,
            unfocusedContainerColor = ProfileFieldNeutral,
            disabledContainerColor = ProfileFieldNeutral,
            errorContainerColor = ProfileFieldNeutral,
            cursorColor = ProfileAccentGold,
            disabledTextColor = Color(0xFF434343),
            disabledPlaceholderColor = Color(0xFF434343).copy(alpha = 0.5f),
            disabledTrailingIconColor = Color(0xFF434343),
        ),
        textStyle = TextStyle(
            color = Color(0xFF434343),
            fontSize = 15.sp.actScaledText(),
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
private fun FieldWarning(text: String, isError: Boolean) {
    Text(
        text = text,
        color = if (isError) ProfileFieldErrorColor else ProfileFieldHintColor,
        fontSize = 12.sp.actScaledText(),
        modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
    )
}
