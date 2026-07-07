package com.revio.app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.app.data.local.preferences.UserPreferences
import com.revio.app.data.local.auth.AuthTokens
import com.revio.app.data.local.auth.TokenStore
import com.revio.app.data.model.AuthProvider
import com.revio.app.core.network.ApiResult
import com.revio.app.data.remote.dto.auth.OnboardingStep
import com.revio.app.data.repository.AuthRepository
import com.revio.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val tokenStore: TokenStore? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun updateConfirmPassword(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun toggleLoginMode() {
        _uiState.update {
            it.copy(
                isLoginMode = !it.isLoginMode,
                password = "",
                confirmPassword = "",
                isPasswordVisible = false,
                isConfirmPasswordVisible = false,
                errorMessage = null,
            )
        }
    }

    fun submitEmailAuth() {
        if (_uiState.value.isLoginMode) loginWithEmail() else registerWithEmail()
    }

    private fun loginWithEmail() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (email.isBlank()) { setError("Email cannot be empty"); return }
        if (password.isBlank()) { setError("Password cannot be empty"); return }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.login(
                email = email,
                password = password,
                googleIdToken = null,
                provider = AuthProvider.REGULAR
            )
            handleAuthResult(result)
        }
    }

    private fun registerWithEmail() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        val confirm = _uiState.value.confirmPassword

        if (email.isBlank()) { setError("Email cannot be empty"); return }
        if (!isValidEmail(email)) { setError("Invalid email format"); return }
        if (password != confirm) { setError("Passwords do not match"); return }
        if (!isValidPassword(password)) {
            setError("Password must be at least 8 characters and include uppercase, lowercase, number and symbol")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.register(
                email = email,
                password = password,
                googleIdToken = null,
                provider = AuthProvider.REGULAR
            )
            handleAuthResult(result)
        }
    }

    fun loginWithGoogle(idToken: String?) {
        if (idToken.isNullOrBlank()) {
            setError("Google sign-in cancelled or failed")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // serverul decide login vs register pentru GOOGLE; trimitem la login endpoint
            val result = authRepository.login(
                email = null,
                password = null,
                googleIdToken = idToken,
                provider = AuthProvider.GOOGLE
            )
            handleAuthResult(result)
            // idToken nu e ținut nicăieri în state — nu se poate reutiliza accidental
        }
    }

    private suspend fun handleAuthResult(result: ApiResult<com.revio.app.data.remote.dto.auth.AuthResponse>) {
        when (result) {
            is ApiResult.Success -> {
                tokenStore?.save(AuthTokens(result.data.accessToken, result.data.refreshToken))
                    ?: userPreferences.saveJwtToken(result.data.accessToken)
                val jwtUserId = result.data.accessToken.extractUserIdFromJwt()
                val navTarget = when (result.data.onboardingStep) {
                    OnboardingStep.PROFILE_REQUIRED -> {
                        AuthNavigationEvent.ToProfileCustomization
                    }
                    OnboardingStep.COMPLETED -> {
                        if (jwtUserId != null) {
                            resolveCompletedProfileDestination(jwtUserId)
                        } else {
                            resolveCompletedProfileDestination()
                        }
                    }
                }
                _uiState.update {
                    it.copy(isLoading = false, navigationEvent = navTarget)
                }
            }
            is ApiResult.Error -> setError(result.message)
        }
    }

    private suspend fun resolveCompletedProfileDestination(jwtUserId: UUID? = null): AuthNavigationEvent {
        return when (val userResult = userRepository.getCurrentUser()) {
            is ApiResult.Success -> {
                userPreferences.saveUserId(jwtUserId ?: userResult.data.id)
                userPreferences.saveUsername(userResult.data.username)
                AuthNavigationEvent.ToFeed
            }
            is ApiResult.Error -> AuthNavigationEvent.ToProfileCustomization
        }
    }

    fun consumeNavigationEvent() {
        _uiState.update { it.copy(navigationEvent = null) }
    }

    private fun isValidEmail(email: String): Boolean {
        val r = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        return r.matches(email)
    }

    private fun isValidPassword(password: String): Boolean =
        password.length >= 8 &&
            password.any(Char::isUpperCase) &&
            password.any(Char::isLowerCase) &&
            password.any(Char::isDigit) &&
            password.any { !it.isLetterOrDigit() }

    private fun setError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                errorId = it.errorId + 1,
                isLoading = false
            )
        }
    }

    fun forgotPassword() {
        setError("Password reset functionality not implemented yet")
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun String.extractUserIdFromJwt(): UUID? {
        return runCatching {
            val payload = split(".").getOrNull(1) ?: return null
            val payloadJson = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
            val claims = Json.parseToJsonElement(payloadJson).jsonObject
            val rawUserId = claims["userId"]?.jsonPrimitive?.contentOrNull
                ?: claims["user_id"]?.jsonPrimitive?.contentOrNull
            rawUserId?.let(UUID::fromString)
        }.getOrNull()
    }

    // For testing — scoate înainte de release
    fun resetOnboardingStatus(onComplete: () -> Unit) {
        viewModelScope.launch {
            userPreferences.resetOnboardingStatus()
            onComplete()
        }
    }
}
