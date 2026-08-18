package com.revio.social.data.remote.dto.auth

import kotlinx.serialization.Serializable

/**
 * Data class representing the response from authentication endpoints.
 */
@Serializable
data class AuthResponse(
    val accessToken: String,
    val onboardingStep: OnboardingStep,
    val refreshToken: String = "legacy-test-refresh",
    val expiresIn: Int = 900,
    val scope: String = "FULL",
    val waitlist: WaitlistPrefillDTO? = null,
)

@Serializable
enum class OnboardingStep {
    PROFILE_REQUIRED,
    COMPLETED
}

/** Mirrors the server's UsernameAvailabilityResult.reason, plus AVAILABLE for a null reason. */
@Serializable
enum class WaitlistUsernameStatus {
    AVAILABLE, TAKEN, INVALID_FORMAT, TOO_SHORT, TOO_LONG
}

/**
 * Present on [AuthResponse] only when the registering email matches a waitlist entry.
 * [suggestedUsername] is raw from Supabase and may not satisfy Revio's username rules, which is
 * exactly what [suggestedUsernameStatus] reports.
 */
@Serializable
data class WaitlistPrefillDTO(
    val suggestedUsername: String? = null,
    val suggestedUsernameStatus: WaitlistUsernameStatus,
)
