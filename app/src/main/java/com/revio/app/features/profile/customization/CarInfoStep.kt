package com.revio.app.features.profile.customization

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.revio.app.core.ui.scaling.LocalProfileScale
import com.revio.app.core.ui.scaling.LocalProfileVSpacingScale
import com.revio.app.core.ui.scaling.profileScaled
import com.revio.app.core.ui.scaling.profileScaledV
import com.revio.app.core.ui.scaling.rememberProfileScale
import com.revio.app.core.ui.scaling.rememberProfileVSpacingScale
import androidx.navigation.NavController
import com.revio.app.core.ui.components.CustomSnackbar
import com.revio.app.core.navigation.Screen
import com.revio.app.features.auth.ScreenBackground
import com.revio.app.features.profile.components.DropdownFieldWithoutOverlay
import com.revio.app.features.profile.components.DropdownOverlay
import com.revio.app.features.profile.components.NextStepButton
import com.revio.app.features.profile.components.PictureContainer
import com.revio.app.features.profile.components.SkipCarInfoText
import kotlin.toString

@Composable
fun CarInfoStep(
    viewModel: ProfileCustomizationViewModel,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            Log.d("ERROR MESSAGE", uiState.errorMessage.toString())
        }
    }


    LaunchedEffect(uiState.isUserCreated) {
        if (uiState.isUserCreated) {
            navController.navigate(Screen.Feed.route) {
                popUpTo(Screen.ProfileCustomization.route) { inclusive = true }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                CustomSnackbar(data.visuals.message)
            }
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScreenBackground {
                CompositionLocalProvider(
                    LocalProfileScale provides rememberProfileScale(),
                    LocalProfileVSpacingScale provides rememberProfileVSpacingScale(),
                ) {
                CarInfoForm(
                    uiState = uiState,
                    onAction = { action ->
                        when (action) {
                            is ProfileCustomizationAction.UpdateCarImage -> viewModel.updateCarImage(
                                action.imageSource
                            )

                            is ProfileCustomizationAction.UpdateCarBrand -> viewModel.updateCarBrand(
                                action.brand
                            )

                            is ProfileCustomizationAction.UpdateCarModel -> viewModel.updateCarModel(
                                action.model
                            )

                            is ProfileCustomizationAction.Complete -> {
                                viewModel.completeProfileSetup()
                            }

                            is ProfileCustomizationAction.PreviousStep -> viewModel.previousStep()
                            else -> {}
                        }
                    }
                )
                } // CompositionLocalProvider
            }
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun CarInfoForm(
    uiState: ProfileCustomizationUiState,
    onAction: (ProfileCustomizationAction) -> Unit,
) {
    val context = LocalContext.current
    var brandDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp.profileScaled(), vertical = 30.dp.profileScaledV()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            PictureContainer(
                currentStep = uiState.currentStep,
                picture = uiState.carPicture,
                text = "Your car picture",
                onImageSelected = { uri ->
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    onAction(
                        ProfileCustomizationAction.UpdateCarImage(
                            ImageSource.Local(
                                uri,
                                mimeType
                            )
                        )
                    )
                },
                onBackPress = { onAction(ProfileCustomizationAction.PreviousStep) }
            )
            Spacer(Modifier.height(39.dp.profileScaledV()))
            DropdownFieldWithoutOverlay(
                modifier = Modifier.padding(bottom = 19.dp.profileScaledV()),
                selectedItem = uiState.selectedBrand,
                label = "Brand",
                onDropdownToggle = {
                    brandDropdownExpanded = !brandDropdownExpanded
                    Log.d("brandDropdownExpanded", brandDropdownExpanded.toString())
                    if (brandDropdownExpanded) modelDropdownExpanded = false
                }
            )
            DropdownFieldWithoutOverlay(
                selectedItem = uiState.selectedModel,
                label = if (uiState.isFetchingModels) "Loading models..." else "Model",
                onDropdownToggle = {
                    if (uiState.selectedBrand.isBlank()) {
                        onAction(ProfileCustomizationAction.UpdateCarModel(""))
                        return@DropdownFieldWithoutOverlay
                    }
                    if (uiState.isFetchingModels || uiState.modelsForSelectedBrand.isEmpty()) {
                        return@DropdownFieldWithoutOverlay
                    }
                    modelDropdownExpanded = !modelDropdownExpanded
                    Log.d("modelDropdownExpanded", modelDropdownExpanded.toString())
                    // Close brand dropdown if model is opened
                    if (modelDropdownExpanded) brandDropdownExpanded = false
                }
            )
            if (uiState.isFetchingModels) {
                Spacer(Modifier.height(60.dp.profileScaledV()))
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp.profileScaledV()))
            } else {
                Spacer(Modifier.height(123.dp.profileScaledV()))
            }
            NextStepButton(
                text = "Next",
                onClick = { onAction(ProfileCustomizationAction.Complete) },
                buttonHeight = 51.dp,
            )
            Spacer(Modifier.height(20.dp.profileScaledV()))
            SkipCarInfoText(
                onClick = { onAction(ProfileCustomizationAction.Complete) },
            )

        }

        DropdownOverlay(
            visible = brandDropdownExpanded,
            items = uiState.allBrands,
            onItemSelected = { brand ->
                onAction(ProfileCustomizationAction.UpdateCarBrand(brand))
                brandDropdownExpanded = false
            },
            onDismiss = { brandDropdownExpanded = false }
        )

        DropdownOverlay(
            visible = modelDropdownExpanded,
            items = uiState.modelsForSelectedBrand.map { it.model },
            onItemSelected = { model ->
                onAction(ProfileCustomizationAction.UpdateCarModel(model))
                modelDropdownExpanded = false
            },
            onDismiss = { modelDropdownExpanded = false }
        )
    }

}
