package com.revio.social.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.notifications.PushTokenRegistrar
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.local.auth.AuthTokens
import com.revio.social.data.local.auth.TokenStore
import com.revio.social.data.model.AuthProvider
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ERROR_CODE_NETWORK
import com.revio.social.core.network.isNetworkError
import com.revio.social.data.remote.dto.auth.AuthErrorCode
import com.revio.social.data.remote.dto.auth.OnboardingStep
import com.revio.social.data.repository.AuthRepository
import com.revio.social.data.repository.UserRepository
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

/** ev. 5 — fired once per attempt, before client-side validation runs. */
private const val EVENT_AUTH_START = "auth_start"

/**
 * ev. 6/7 — single event for the whole attempt's outcome, whether it failed client-side
 * validation (pas 2.2a, fired from [loginWithEmail]/[registerWithEmail]) or round-tripped to
 * the server (pas 2.2b, fired from [handleAuthResult] with an [AuthErrorCode] failure code) —
 * the funnel (`auth_start` → `auth_result{outcome=...}`) stays a single event series either way.
 */
private const val EVENT_AUTH_RESULT = "auth_result"

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val tokenStore: TokenStore? = null,
    private val analyticsClient: AnalyticsClient? = null,
    private val pushTokenRegistrar: PushTokenRegistrar? = null,
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

    private fun logAuthStart(method: String) {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_AUTH_START,
                params = mapOf("method" to AnalyticsParamValue.StringValue(method)),
            )
        )
    }

    /**
     * [failureCode] is a fixed set for client-side validation only: `email_empty`,
     * `password_empty`, `invalid_email_format`, `password_mismatch`, `weak_password`.
     */
    private fun logAuthValidationFailure(failureCode: String) {
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_AUTH_RESULT,
                params = mapOf(
                    "outcome" to AnalyticsParamValue.StringValue("failure"),
                    "failure_code" to AnalyticsParamValue.StringValue(failureCode),
                ),
            )
        )
    }

    private fun loginWithEmail() {
        logAuthStart("email_login")
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (email.isBlank()) { logAuthValidationFailure("email_empty"); setError("Email cannot be empty"); return }
        if (password.isBlank()) { logAuthValidationFailure("password_empty"); setError("Password cannot be empty"); return }

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
        logAuthStart("email_register")
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        val confirm = _uiState.value.confirmPassword

        if (email.isBlank()) { logAuthValidationFailure("email_empty"); setError("Email cannot be empty"); return }
        if (!isValidEmail(email)) { logAuthValidationFailure("invalid_email_format"); setError("Invalid email format"); return }
        if (password != confirm) { logAuthValidationFailure("password_mismatch"); setError("Passwords do not match"); return }
        if (!isValidPassword(password)) {
            logAuthValidationFailure("weak_password")
            setError("8+ characters with uppercase, lowercase, number, and symbol.")
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

    private suspend fun handleAuthResult(result: ApiResult<com.revio.social.data.remote.dto.auth.AuthResponse>) {
        when (result) {
            is ApiResult.Success -> {
                logAuthResult(outcome = "success")
                tokenStore?.save(AuthTokens(result.data.accessToken, result.data.refreshToken))
                    ?: userPreferences.saveJwtToken(result.data.accessToken)
                pushTokenRegistrar?.registerCurrentToken()
                val jwtUserId = result.data.accessToken.extractUserIdFromJwt()
                val navTarget = when (result.data.onboardingStep) {
                    OnboardingStep.PROFILE_REQUIRED -> {
                        AuthNavigationEvent.ToProfileCustomization(result.data.waitlist)
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
            is ApiResult.Error -> {
                logAuthResult(outcome = "failure", failureCode = resolveAuthFailureCode(result))
                // result.code is the machine-readable code — keep reading it so a suspended
                // account gets its own blocking dialog instead of a dismissible snackbar.
                if (result.code == AuthErrorCode.ACCOUNT_SUSPENDED.name) {
                    _uiState.update { it.copy(isLoading = false, accountSuspendedMessage = result.message) }
                } else {
                    setError(result.message)
                }
            }
        }
    }

    private fun logAuthResult(outcome: String, failureCode: String? = null) {
        val params = buildMap<String, AnalyticsParamValue> {
            put("outcome", AnalyticsParamValue.StringValue(outcome))
            failureCode?.let { put("failure_code", AnalyticsParamValue.StringValue(it)) }
        }
        analyticsClient?.log(AnalyticsEvent(name = EVENT_AUTH_RESULT, params = params))
    }

    /**
     * ev. 7 — [AuthErrorCode] when [result]'s code matches the enum (pas 1.0 keeps it in sync
     * with the server); [com.revio.social.core.network.ERROR_CODE_NETWORK] for offline; a fixed
     * fallback for anything else (e.g. a 5xx with no machine-readable code).
     */
    private fun resolveAuthFailureCode(result: ApiResult.Error): String {
        if (result.isNetworkError) return ERROR_CODE_NETWORK
        return AuthErrorCode.entries.find { it.name == result.code }?.name ?: "unrecognized"
    }

    private suspend fun resolveCompletedProfileDestination(jwtUserId: UUID? = null): AuthNavigationEvent {
        return when (val userResult = userRepository.getCurrentUser()) {
            is ApiResult.Success -> {
                userPreferences.saveUserId(jwtUserId ?: userResult.data.id)
                userPreferences.saveUsername(userResult.data.username)
                AuthNavigationEvent.ToFeed
            }
            is ApiResult.Error -> AuthNavigationEvent.ToProfileCustomization()
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

    fun onAccountSuspendedShown() {
        _uiState.update { it.copy(accountSuspendedMessage = null) }
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

}
