package com.revio.app.features.settings.deleteaccount

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.revio.app.core.navigation.Screen
import com.revio.app.core.ui.components.AppScreenBackground
import com.revio.app.core.ui.scaling.LocalActivityScale
import com.revio.app.core.ui.scaling.LocalProfileScale
import com.revio.app.core.ui.scaling.actScaled
import com.revio.app.core.ui.scaling.actScaledText
import com.revio.app.core.ui.scaling.rememberActivityScale
import com.revio.app.core.ui.theme.Poppins

@Composable
fun DeleteAccountScreen(
    navController: NavController,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.deletionCompleted) {
        if (uiState.deletionCompleted) {
            navController.navigate(Screen.Auth.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val onBack: () -> Unit = {
        if (uiState.currentStep != DeleteAccountStep.Reason) {
            viewModel.onAction(DeleteAccountAction.PreviousStep)
        } else {
            navController.popBackStack()
        }
    }

    BackHandler(enabled = uiState.currentStep != DeleteAccountStep.Reason) {
        viewModel.onAction(DeleteAccountAction.PreviousStep)
    }

    AppScreenBackground {
        val activityScale = rememberActivityScale()
        CompositionLocalProvider(
            LocalActivityScale provides activityScale,
            LocalProfileScale provides activityScale,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp.actScaled()),
            ) {
                // ── Top bar ──────────────────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp.actScaled()))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp.actScaled()),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp.actScaled()))
                    Text(
                        text = "Delete account",
                        color = Color.White,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Medium,
                        fontSize = 25.sp.actScaledText(),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp.actScaled()))

                when (uiState.currentStep) {
                    DeleteAccountStep.Reason -> ReasonStep(uiState = uiState, onAction = viewModel::onAction)
                    DeleteAccountStep.Retention -> RetentionStep(uiState = uiState, onAction = viewModel::onAction)
                    DeleteAccountStep.Confirm -> ConfirmStep(
                        uiState = uiState,
                        onAction = viewModel::onAction,
                        onKeepAccount = {
                            viewModel.onAction(DeleteAccountAction.KeepAccount)
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}
