package com.revio.app.features.profile.customization

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.revio.app.core.navigation.Screen
import com.revio.app.core.ui.scaling.LocalProfileScale
import com.revio.app.core.ui.scaling.LocalProfileVSpacingScale
import com.revio.app.core.ui.scaling.profileScaled
import com.revio.app.core.ui.scaling.profileScaledV
import com.revio.app.core.ui.scaling.rememberProfileScale
import com.revio.app.core.ui.scaling.rememberProfileVSpacingScale
import com.revio.app.core.ui.components.CustomSnackbar
import com.revio.app.features.auth.ScreenBackground
import com.revio.app.features.profile.components.BirthDateField
import com.revio.app.features.profile.components.CountryDropdown
import com.revio.app.features.profile.components.LabeledTextField
import com.revio.app.features.profile.components.NextStepButton
import com.revio.app.features.profile.components.PictureContainer

@Composable
fun PersonalInfoStep(
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
                PersonalInfoForm(
                    uiState = uiState,
                    onAction = { action ->
                        when (action) {
                            is ProfileCustomizationAction.UpdateProfileImage -> viewModel.updateProfileImage(
                                action.imageSource
                            )

                            is ProfileCustomizationAction.UpdateProfileTransform -> viewModel.onProfileTransformChanged(
                                action.state
                            )

                            is ProfileCustomizationAction.UpdateFullName -> viewModel.updateFullName(
                                action.fullName
                            )

                            is ProfileCustomizationAction.UpdateUsername -> viewModel.updateUsername(
                                action.username
                            )

                            is ProfileCustomizationAction.UpdateBirthDate -> viewModel.updateBirthDate(
                                action.birthDate
                            )

                            is ProfileCustomizationAction.UpdateCountry -> viewModel.updateCountry(
                                action.country
                            )

                            is ProfileCustomizationAction.NextStep -> viewModel.nextStep()
                            else -> {}
                        }
                    },
                )
                } // CompositionLocalProvider
            }
            if (uiState.isFetchingBrands || uiState.isLoading) {
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
private fun PersonalInfoForm(
    uiState: ProfileCustomizationUiState,
    onAction: (ProfileCustomizationAction) -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp.profileScaled(), vertical = 30.dp.profileScaledV()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        PictureContainer(
            currentStep = uiState.currentStep,
            picture = uiState.profilePicture,
            text = "Your profile picture",
            onImageSelected = { uri ->
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                onAction(
                    ProfileCustomizationAction.UpdateProfileImage(
                        ImageSource.Local(
                            uri,
                            mimeType
                        )
                    )
                )
            },
            onTransformChanged = { state ->
                onAction(ProfileCustomizationAction.UpdateProfileTransform(state))
            },
        )
        Spacer(Modifier.height(23.dp.profileScaledV()))
        LabeledTextField(
            label = "Full name",
            value = uiState.fullName,
            onValueChange = { onAction(ProfileCustomizationAction.UpdateFullName(it)) },
            placeholderText = "Josh Michael"
        )
        Spacer(Modifier.height(23.dp.profileScaledV()))
        LabeledTextField(
            label = "Username",
            value = uiState.username,
            onValueChange = { onAction(ProfileCustomizationAction.UpdateUsername(it)) },
            placeholderText = "Josh94"
        )
        Spacer(Modifier.height(23.dp.profileScaledV()))
        BirthDateField(
            birthDate = uiState.birthDate,
            onBirthDateChanged = { onAction(ProfileCustomizationAction.UpdateBirthDate(it)) }
        )
        Spacer(Modifier.height(23.dp.profileScaledV()))
        CountryDropdown(
            selectedCountry = uiState.country,
            onCountrySelected = { onAction(ProfileCustomizationAction.UpdateCountry(it.name)) }
        )

        Spacer(Modifier.height(40.dp.profileScaledV()))

        NextStepButton(
            text = "Finish",
            onClick = { onAction(ProfileCustomizationAction.NextStep) },
        )
    }
}