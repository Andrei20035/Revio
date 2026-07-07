package com.revio.app.core.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Auth : Screen("auth")
    object ProfileCustomization : Screen("profile_customization")
    object Feed : Screen("feed")
    object Settings : Screen("settings")
    object Map : Screen("map")
    object Profile : Screen("profile") {
        const val ARG_USER_ID = "userId"
        const val FOREIGN_ROUTE = "profile/{$ARG_USER_ID}"

        fun createRoute(userId: java.util.UUID): String = "profile/$userId"
    }
    object Camera : Screen("camera")
    object Leaderboard : Screen("leaderboard")
    object Activity : Screen("activity")

    object EditProfile : Screen("edit_profile")
    object PersonalInfo : Screen("personal_info")
    object ChangePassword : Screen("change_password")
    object PrivacyPolicy : Screen("privacy_policy")
    object TermsConditions : Screen("terms_conditions")

    /**
     * Upload-image screen reached after the user captures/picks a photo from the
     * "Post your find" overlay. Carries the selected image URI and post source as query arguments.
     */
    object ImageUpload : Screen("image_upload?imageUri={imageUri}&source={source}") {
        const val ARG_IMAGE_URI = "imageUri"
        const val ARG_SOURCE = "source"

        /** Builds the concrete route for [imageUri], encoding it for safe nav-arg transport. */
        fun createRoute(imageUri: String, source: String = "GALLERY"): String =
            "image_upload?imageUri=${Uri.encode(imageUri)}&source=$source"
    }
}
