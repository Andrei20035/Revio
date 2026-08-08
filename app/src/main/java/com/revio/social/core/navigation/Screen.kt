package com.revio.social.core.navigation

import android.net.Uri
import java.util.UUID

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
    object Notifications : Screen("notifications")
    object AdminHome : Screen("admin")
    object AdminReports : Screen("admin/reports")
    object AdminUser : Screen("admin/user")

    /** Weekend-challenge detail screen. */
    object ChallengeDetail : Screen("challenge/{challengeId}") {
        const val ARG_CHALLENGE_ID = "challengeId"

        fun createRoute(challengeId: UUID): String = "challenge/$challengeId"
    }

    /** Lists the caller's own challenge participation: summary, current, and history. */
    object MyChallenges : Screen("my_challenges")

    object EditProfile : Screen("edit_profile")
    object PersonalInfo : Screen("personal_info")
    object ChangePassword : Screen("change_password")
    object DeleteAccount : Screen("delete_account")
    object PrivacyPolicy : Screen("privacy_policy")
    object TermsConditions : Screen("terms_conditions")

    /**
     * Upload-image screen reached after the user captures/picks a photo from the
     * "Post your find" overlay. Carries the selected image URI and post source as query arguments.
     */
    object ImageUpload : Screen("image_upload?imageUri={imageUri}&source={source}&postId={postId}") {
        const val ARG_IMAGE_URI = "imageUri"
        const val ARG_SOURCE = "source"
        const val ARG_POST_ID = "postId"

        /** Builds the concrete route for [imageUri], encoding it for safe nav-arg transport. */
        fun createRoute(imageUri: String, source: String = "GALLERY"): String =
            "image_upload?imageUri=${Uri.encode(imageUri)}&source=$source"

        /** Builds the concrete route for editing an existing post identified by [postId]. */
        fun createEditRoute(postId: String): String = "image_upload?postId=$postId"
    }

    /**
     * Settings feedback form. Carries the submission source and, optionally, the screen the
     * form was opened from — read via `SavedStateHandle` in `FeedbackViewModel`.
     */
    object Feedback : Screen("feedback?source={source}&screen={screen}") {
        const val ARG_SOURCE = "source"
        const val ARG_ORIGIN_SCREEN = "screen"

        fun createRoute(source: String = "settings_feedback", originScreen: String? = null): String {
            val screenParam = originScreen?.let { "&screen=${Uri.encode(it)}" } ?: ""
            return "feedback?source=$source$screenParam"
        }
    }
}
