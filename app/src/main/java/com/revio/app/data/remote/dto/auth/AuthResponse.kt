package com.revio.app.data.remote.dto.auth

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
)

@Serializable
enum class OnboardingStep {
    PROFILE_REQUIRED,
    COMPLETED
}
