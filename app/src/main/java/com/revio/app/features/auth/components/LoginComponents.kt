package com.revio.app.features.auth.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.app.R

/**
 * Email input field component.
 */
@Composable
fun EmailField(
    email: String?,
    onEmailChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next
) {
    Text(
        text = "Email address",
        color = Color.Gray,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, start = 8.dp)
    )

    OutlinedTextField(
        value = email ?: "",
        onValueChange = onEmailChange,
        placeholder = {
            Text(
                text = "josh253@gmail.com",
                color = Color.Gray.copy(alpha = 0.6f)
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .height(60.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color.Gray.copy(alpha = 0.2f),
            unfocusedContainerColor = Color.Gray.copy(alpha = 0.2f)
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = imeAction
        ),
        textStyle = TextStyle(
            color = Color.Black,
            fontSize = 16.sp
        )
    )
}

/**
 * Password input field component.
 */
@Composable
fun PasswordField(
    password: String?,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Password",
    imeAction: ImeAction = ImeAction.Done
) {

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(bottom = 8.dp, start = 8.dp)
        )

        OutlinedTextField(
            value = password ?: "",
            onValueChange = onPasswordChange,
            placeholder = {
                Text(
                    text = "••••••••",
                    color = Color.Gray.copy(alpha = 0.6f)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .height(60.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Gray.copy(alpha = 0.2f),
                unfocusedContainerColor = Color.Gray.copy(alpha = 0.2f)
            ),
            textStyle = TextStyle(
                color = Color.Black,
                fontSize = 16.sp
            ),
            visualTransformation = if (isPasswordVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        painter = painterResource(
                            id = if (isPasswordVisible) R.drawable.visibility_off_24px else R.drawable.visibility_24px
                        ),
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        modifier = Modifier.size(24.dp),
                        tint = Color.Black
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = imeAction
            )
        )
    }
}

/**
 * Forgot password text component.
 */
@Composable
fun ForgotPasswordText(
    onForgotPasswordClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Forgot Password?",
        color = Color.Gray,
        fontSize = 14.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .clickable(onClick = onForgotPasswordClick),
        textAlign = TextAlign.End
    )
}

/**
 * Primary action button (Login/Sign Up).
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF0AB25),
            disabledContainerColor = Color(0xFFF0AB25).copy(alpha = 0.7f),
            disabledContentColor = Color.Black.copy(alpha = 0.7f)
        ),
        enabled = !isLoading
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Google sign in button.
 */
@Composable
fun GoogleSignInButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(63.dp)
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Gray.copy(alpha = 0.2f),
            disabledContainerColor = Color.Gray.copy(alpha = 0.7f),
            disabledContentColor = Color.Black.copy(alpha = 0.7f)
        ),
        border = BorderStroke(0.dp, Color.Transparent),
        enabled = !isLoading
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.google_logo),
                contentDescription = "Google",
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Auth mode switch text (Login/Sign Up).
 */
@Composable
fun AuthModeSwitchText(
    isLoginMode: Boolean,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = if (isLoginMode) "Don't have an account? " else "Already have an account? ",
            color = Color.Gray,
            fontSize = 14.sp
        )
        Text(
            text = if (isLoginMode) "Sign Up" else "Log In",
            color = Color(0xFFF59E0B),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onToggleMode)
        )
    }
}

