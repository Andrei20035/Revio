package com.revio.app.core.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.revio.app.features.activity.ActivityScreen
import com.revio.app.features.feed.FeedScreen
import com.revio.app.features.upload.ImageUploadScreen
import com.revio.app.features.auth.AuthScreen
import com.revio.app.features.onboarding.OnboardingScreen
import com.revio.app.features.profile.dashboard.ProfileDashboardScreen
import com.revio.app.features.profile.customization.ProfileCustomization
import com.revio.app.features.leaderboard.LeaderboardScreen
import com.revio.app.features.settings.PlaceholderScreen
import com.revio.app.features.settings.PrivacyPolicyScreen
import com.revio.app.features.settings.SettingsScreen
import com.revio.app.features.settings.TermsAndConditionsScreen
import com.revio.app.features.settings.personalinfo.PersonalInfoScreen
import com.revio.app.features.settings.changepassword.ChangePasswordScreen

@Composable
fun RevioNavigation(
    navController: NavHostController,
    startDestination: String,
    ) {
    val instantDestinationRoutes = setOf(
        Screen.Feed.route,
        Screen.Leaderboard.route,
        Screen.Activity.route,
        Screen.Profile.route,
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            if (targetState.destination.route in instantDestinationRoutes) {
                EnterTransition.None
            } else {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(250),
                )
            }
        },
        exitTransition = {
            if (targetState.destination.route in instantDestinationRoutes) {
                ExitTransition.None
            } else {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(250),
                )
            }
        },
        popEnterTransition = {
            val isReturningFromSettingsToProfile =
                initialState.destination.route == Screen.Settings.route &&
                    targetState.destination.route == Screen.Profile.route

            if (
                targetState.destination.route in instantDestinationRoutes &&
                !isReturningFromSettingsToProfile
            ) {
                EnterTransition.None
            } else {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(250),
                )
            }
        },
        popExitTransition = {
            val isReturningFromSettingsToProfile =
                initialState.destination.route == Screen.Settings.route &&
                    targetState.destination.route == Screen.Profile.route

            if (
                targetState.destination.route in instantDestinationRoutes &&
                !isReturningFromSettingsToProfile
            ) {
                ExitTransition.None
            } else {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(250),
                )
            }
        },
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
            PersonalInfoScreen(
                navController = navController,
                isNavTransitionRunning = transition.isRunning,
            )
        }

        composable(Screen.ChangePassword.route) {
            ChangePasswordScreen(navController = navController)
        }

        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(navController = navController)
        }

        composable(Screen.TermsConditions.route) {
            TermsAndConditionsScreen(navController = navController)
        }

        // Add other screens...
    }
}
