package com.revio.app.features.profile.customization

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@Composable
fun ProfileCustomization(
    navController: NavController,
    viewModel: ProfileCustomizationViewModel = hiltViewModel(),
) {
    PersonalInfoStep(
        viewModel = viewModel,
        navController = navController,
    )
}