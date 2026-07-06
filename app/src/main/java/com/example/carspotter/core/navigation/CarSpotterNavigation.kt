package com.example.carspotter.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.carspotter.features.activity.ActivityScreen
import com.example.carspotter.features.feed.FeedScreen
import com.example.carspotter.features.upload.ImageUploadScreen
import com.example.carspotter.features.auth.AuthScreen
import com.example.carspotter.features.onboarding.OnboardingScreen
import com.example.carspotter.features.profile.dashboard.ProfileDashboardScreen
import com.example.carspotter.features.profile.customization.ProfileCustomization
import com.example.carspotter.features.leaderboard.LeaderboardScreen
import com.example.carspotter.features.settings.PlaceholderScreen
import com.example.carspotter.features.settings.SettingsScreen
import com.example.carspotter.features.settings.personalinfo.PersonalInfoScreen

@Composable
fun CarSpotterNavigation(
    navController: NavHostController,
    startDestination: String,
    ) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                navController = navController,
                onComplete = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Onboarding.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(Screen.Auth.route) {
            AuthScreen(
                navController = navController
            )
        }

        composable(Screen.ProfileCustomization.route) {
            ProfileCustomization(
                navController = navController
            )
        }

        composable(Screen.Feed.route) {
            FeedScreen(
                navController = navController
            )
        }

        composable(Screen.Profile.route) {
            ProfileDashboardScreen(
                navController = navController
            )
        }

        composable(
            route = Screen.Profile.FOREIGN_ROUTE,
            arguments = listOf(
                navArgument(Screen.Profile.ARG_USER_ID) {
                    type = NavType.StringType
                },
            ),
        ) {
            // The userId nav arg is read by ProfileDashboardViewModel via SavedStateHandle.
            ProfileDashboardScreen(navController = navController)
        }

        composable(
            route = Screen.ImageUpload.route,
            arguments = listOf(
                navArgument(Screen.ImageUpload.ARG_IMAGE_URI) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Screen.ImageUpload.ARG_SOURCE) {
                    type = NavType.StringType
                    defaultValue = "GALLERY"
                },
            ),
        ) {
            // The image URI and source nav args are read by ImageUploadViewModel via SavedStateHandle.
            ImageUploadScreen(navController = navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController
            )
        }

        composable(Screen.Leaderboard.route) {
            LeaderboardScreen(navController = navController)
        }

        composable(Screen.Activity.route) {
            ActivityScreen(navController = navController)
        }

        composable(Screen.EditProfile.route) {
            PlaceholderScreen(title = "Edit Profile", navController = navController)
        }

        composable(Screen.PersonalInfo.route) {
            PersonalInfoScreen(navController = navController)
        }

        composable(Screen.ChangePassword.route) {
            PlaceholderScreen(title = "Change password", navController = navController)
        }

        composable(Screen.PrivacyPolicy.route) {
            PlaceholderScreen(title = "Privacy Policy", navController = navController)
        }

        composable(Screen.TermsConditions.route) {
            PlaceholderScreen(title = "Terms & Conditions", navController = navController)
        }

        // Add other screens...
    }
}
