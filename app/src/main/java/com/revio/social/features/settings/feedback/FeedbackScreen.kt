package com.revio.social.features.settings.feedback

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import com.revio.social.core.ui.components.AppScreenBackground
import com.revio.social.core.ui.scaling.LocalActivityScale
import com.revio.social.core.ui.scaling.LocalProfileScale
import com.revio.social.core.ui.scaling.actScaled
import com.revio.social.core.ui.scaling.actScaledText
import com.revio.social.core.ui.scaling.rememberActivityScale
import com.revio.social.core.ui.theme.Poppins

@Composable
fun FeedbackScreen(
    navController: NavController,
    viewModel: FeedbackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val onBack: () -> Unit = {
        when (uiState.step) {
            FeedbackStep.CategoryPicker, FeedbackStep.Sent -> navController.popBackStack()
            FeedbackStep.Form -> viewModel.onAction(FeedbackAction.PreviousStep)
            FeedbackStep.Review -> viewModel.onAction(FeedbackAction.PreviousStep)
        }
    }

    BackHandler(enabled = uiState.step != FeedbackStep.CategoryPicker && uiState.step != FeedbackStep.Sent) {
        viewModel.onAction(FeedbackAction.PreviousStep)
    }

    AppScreenBackground(showBottomScrim = false) {
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
                    .imePadding()
                    .padding(horizontal = 10.dp.actScaled()),
            ) {
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
                        text = "Feedback & ideas",
                        color = Color.White,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Medium,
                        fontSize = 25.sp.actScaledText(),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp.actScaled()))

                when (uiState.step) {
                    FeedbackStep.CategoryPicker -> CategoryPickerStep(onAction = viewModel::onAction)
                    FeedbackStep.Form -> FeedbackFormStep(uiState = uiState, onAction = viewModel::onAction)
                    FeedbackStep.Review -> FeedbackReviewStep(
                        uiState = uiState,
                        onAction = viewModel::onAction,
                        onCancel = { navController.popBackStack() },
                    )
                    FeedbackStep.Sent -> FeedbackSentStep(
                        onDone = { navController.popBackStack() },
                        onSendAnother = { viewModel.onAction(FeedbackAction.SendAnother) },
                    )
                }

                Spacer(modifier = Modifier.height(32.dp.actScaled()))
            }
        }
    }
}
