package com.revio.social.features.auth

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.revio.social.BuildConfig
import com.revio.social.core.ui.components.CustomSnackbar
import com.revio.social.core.ui.components.GradientText
import com.revio.social.core.navigation.Screen
import com.revio.social.core.ui.overlay.DimOnlyDialogWindow
import com.revio.social.core.ui.theme.Poppins
import com.revio.social.features.auth.components.AuthModeSwitchText
import com.revio.social.features.auth.components.EmailField
import com.revio.social.features.auth.components.ForgotPasswordText
import com.revio.social.features.auth.components.GoogleSignInButton
import com.revio.social.features.auth.components.PasswordField
import com.revio.social.features.auth.components.PrimaryActionButton
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.delay

/**
 * Login screen that allows users to sign in or create a new account.
 */
@Composable
fun AuthScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorId) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message = msg)
        viewModel.onErrorShown()
    }

    LaunchedEffect(uiState.navigationEvent) {
        when (uiState.navigationEvent) {
            AuthNavigationEvent.ToProfileCustomization -> {
                navController.navigate(Screen.ProfileCustomization.route) {
                    popUpTo(Screen.Auth.route) { inclusive = true }
                }
                viewModel.consumeNavigationEvent()
            }
            AuthNavigationEvent.ToFeed -> {
                navController.navigate(Screen.Feed.route) {
                    popUpTo(Screen.Auth.route) { inclusive = true }
                }
                viewModel.consumeNavigationEvent()
            }
            null -> Unit
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                CustomSnackbar(data.visuals.message)
            }
        }
    ) { padding ->
        Log.d("PADDING", padding.toString())
            Box(modifier= Modifier
                .fillMaxSize()
                .padding(padding)
            ) {
                ScreenBackground {
                    LoginHeader()
                    LoginCard(
                        uiState = uiState,
                        onAction = { action ->
                            when (action) {
                                is AuthAction.EmailChanged -> viewModel.updateEmail(action.email)
                                is AuthAction.PasswordChanged -> viewModel.updatePassword(action.password)
                                is AuthAction.ConfirmPasswordChanged -> viewModel.updateConfirmPassword(action.password)
                                is AuthAction.TogglePasswordVisibility -> viewModel.togglePasswordVisibility()
                                is AuthAction.ToggleConfirmPasswordVisibility -> viewModel.toggleConfirmPasswordVisibility()
                                is AuthAction.SubmitEmailAuth -> viewModel.submitEmailAuth()
                                is AuthAction.GoogleSignInResult -> viewModel.loginWithGoogle(action.idToken)
                                is AuthAction.ToggleMode -> viewModel.toggleLoginMode()
                                is AuthAction.ForgotPassword -> viewModel.forgotPassword()
                                is AuthAction.ResetOnboarding -> { /* test-only */ }
                            }
                        }
                    )
                }
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(enabled = false) { },
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                val suspendedMessage = uiState.accountSuspendedMessage
                if (suspendedMessage != null) {
                    AccountSuspendedDialog(
                        message = suspendedMessage,
                        onDismiss = { viewModel.onAccountSuspendedShown() },
                    )
                }
            }

    }
}

private val SuspendedDialogSurface = Color(0xFF1B1F33)
private val SuspendedDialogBorder = Color(0x1FFFFFFF)

/**
 * Blocking notice for a banned account: no tap-outside/back-press dismissal, only the explicit
 * button acknowledges it. The message comes straight from the server (reason, expiry or
 * "permanently", and the contact email are already baked into it — see AuthRoutes.kt's
 * banSuspensionMessage on the server), so this dialog just renders it.
 */
@Composable
private fun AccountSuspendedDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        DimOnlyDialogWindow()

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(SuspendedDialogSurface)
                    .border(1.dp, SuspendedDialogBorder, RoundedCornerShape(20.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Account suspended",
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                PrimaryActionButton(
                    text = "OK",
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
fun ScreenBackground(
    content: @Composable () -> Unit
) {
    val gradientColors = arrayOf(
        0.0f to Color(0xFF000000),
        0.4f to Color(0xFF05071B)
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colorStops = gradientColors,
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY,
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}

@Composable
private fun LoginHeader() {
    Spacer(modifier = Modifier.height(45.dp))

    GradientText(
        text = "Revio",
        fontSize = 60.sp,
        fontWeight = FontWeight.Bold,
    )

    Text(
        text = "Spot. Snap. Share.",
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(80.dp))
}

@Composable
private fun LoginCard(
    uiState: AuthUiState,
    onAction: (AuthAction) -> Unit
) {
    var shouldShowConfirm by remember { mutableStateOf(false) }
    var shouldShowForgot by remember { mutableStateOf(false) }

    val cardHeight by animateDpAsState(
        targetValue = if (uiState.isLoginMode) 480.dp else 620.dp,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "cardHeight"
    )

    LaunchedEffect(uiState.isLoginMode) {
        if (!uiState.isLoginMode) {
            delay(300L)
            shouldShowConfirm = true
        } else {
            shouldShowConfirm = false
        }
    }

    LaunchedEffect(uiState.isLoginMode) {
        if (uiState.isLoginMode) {
            delay(260L)
            shouldShowForgot = true
        } else {
            shouldShowForgot = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoginForm(
                uiState = uiState,
                shouldShowConfirm = shouldShowConfirm,
                shouldShowForgot = shouldShowForgot,
                onAction = onAction
            )

            Spacer(Modifier.weight(1f))

            LoginActions(
                uiState = uiState,
                onAction = onAction
            )

            LoginFooter(
                uiState = uiState,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun LoginForm(
    uiState: AuthUiState,
    shouldShowConfirm: Boolean,
    shouldShowForgot: Boolean,
    onAction: (AuthAction) -> Unit
) {
    EmailField(
        email = uiState.email,
        onEmailChange = { onAction(AuthAction.EmailChanged(it)) }
    )

    PasswordField(
        password = uiState.password,
        onPasswordChange = { onAction(AuthAction.PasswordChanged(it)) },
        isPasswordVisible = uiState.isPasswordVisible,
        onTogglePasswordVisibility = { onAction(AuthAction.TogglePasswordVisibility) },
        imeAction = if (uiState.isLoginMode) ImeAction.Done else ImeAction.Next
    )

    AnimatedVisibility(
        visible = shouldShowConfirm,
        enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(10, easing = FastOutSlowInEasing))
    ) {
        PasswordField(
            password = uiState.confirmPassword,
            onPasswordChange = { onAction(AuthAction.ConfirmPasswordChanged(it)) },
            isPasswordVisible = uiState.isConfirmPasswordVisible,
            onTogglePasswordVisibility = { onAction(AuthAction.ToggleConfirmPasswordVisibility) },
            label = "Confirm Password",
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    AnimatedVisibility(
        visible = shouldShowForgot,
        enter = fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
    ) {
        ForgotPasswordText(
            onForgotPasswordClick = { onAction(AuthAction.ForgotPassword) }
        )
    }

    if (!uiState.isLoginMode) {
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LoginActions(
    uiState: AuthUiState,
    onAction: (AuthAction) -> Unit
) {
    PrimaryActionButton(
        text = if (uiState.isLoginMode) "Log In" else "Sign Up",
        onClick = { onAction(AuthAction.SubmitEmailAuth) },
        isLoading = uiState.isLoading
    )

    GoogleSignInHandler(
        text = if (uiState.isLoginMode) "Log In with Google" else "Sign Up with Google",
        isLoading = uiState.isLoading,
        onGoogleSignIn = { idToken ->
            onAction(AuthAction.GoogleSignInResult(idToken))
        }
    )
}

@Composable
private fun LoginFooter(
    uiState: AuthUiState,
    onAction: (AuthAction) -> Unit
) {
    Row {
        // For testing: Reset onboarding
        Text(
            text = "Reset ",
            color = Color.Red,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onAction(AuthAction.ResetOnboarding) }
        )

        AuthModeSwitchText(
            isLoginMode = uiState.isLoginMode,
            onToggleMode = { onAction(AuthAction.ToggleMode) }
        )
    }
}

@Composable
private fun GoogleSignInHandler(
    text: String,
    isLoading: Boolean,
    onGoogleSignIn: (String?) -> Unit
) {
    val context = LocalContext.current

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(BuildConfig.WEB_CLIENT_ID)
            .build()
    }

    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            onGoogleSignIn(account.idToken)
        } catch (e: ApiException) {
            Log.e("GoogleSignIn", "Google sign in failed", e)
            onGoogleSignIn(null)
        }
    }

    GoogleSignInButton(
        text = text,
        onClick = {
            val signInIntent = googleSignInClient.signInIntent
            launcher.launch(signInIntent)
        },
        isLoading = isLoading
    )
}