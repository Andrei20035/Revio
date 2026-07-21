package com.revio.app.data.repository

import com.revio.app.data.local.preferences.UserPreferences
import com.revio.app.data.local.auth.AuthTokens
import com.revio.app.data.local.auth.DeviceIdentity
import com.revio.app.data.local.auth.TokenStore
import com.revio.app.data.remote.api.AuthApi
import com.revio.app.data.remote.dto.auth.AuthResponse
import com.revio.app.data.remote.dto.auth.DeleteAccountRequest
import com.revio.app.data.remote.dto.auth.DeletionContextDto
import com.revio.app.data.remote.dto.auth.LoginRequest
import com.revio.app.data.remote.dto.auth.RegisterRequest
import com.revio.app.data.remote.dto.auth.UpdatePasswordRequest
import com.revio.app.data.model.AuthProvider
import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.safeApiCall
import com.revio.app.core.network.safeApiCallNoContent
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    suspend fun login(email: String?, password: String?, googleIdToken: String?, provider: AuthProvider): ApiResult<AuthResponse>
    suspend fun register(email: String?, password: String?, googleIdToken: String?, provider: AuthProvider): ApiResult<AuthResponse>
    suspend fun deleteAccount(request: DeleteAccountRequest): ApiResult<Unit>
    suspend fun getDeletionContext(): ApiResult<DeletionContextDto>
    suspend fun updatePassword(oldPassword: String, newPassword: String): ApiResult<AuthResponse>
    suspend fun logout(): ApiResult<Unit>
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val userPreferences: UserPreferences,
    private val tokenStore: TokenStore? = null,
    private val deviceIdentity: DeviceIdentity? = null,
) : AuthRepository {

    override suspend fun login(email: String?, password: String?, googleIdToken: String?, provider: AuthProvider): ApiResult<AuthResponse> {
        val loginRequest = LoginRequest(
            email, password, googleIdToken, provider,
            deviceIdentity?.id.orEmpty(), deviceIdentity?.name.orEmpty()
        )
        return safeApiCall { authApi.login(loginRequest) }
    }

    override suspend fun register(email: String?, password: String?, googleIdToken: String?, provider: AuthProvider): ApiResult<AuthResponse> {
        val registerRequest = RegisterRequest(
            email, password, provider, googleIdToken,
            deviceIdentity?.id.orEmpty(), deviceIdentity?.name.orEmpty()
        )
        return safeApiCall { authApi.register(registerRequest) }
    }

    override suspend fun deleteAccount(request: DeleteAccountRequest): ApiResult<Unit> {
        val result = safeApiCall { authApi.deleteAccount(request) }

        if (result is ApiResult.Success) {
            tokenStore?.clear()
            userPreferences.clearAuthData()
        }

        return result
    }

    override suspend fun getDeletionContext(): ApiResult<DeletionContextDto> {
        return safeApiCall { authApi.getDeletionContext() }
    }

    override suspend fun updatePassword(oldPassword: String, newPassword: String): ApiResult<AuthResponse> {
        val updatePasswordRequest = UpdatePasswordRequest(oldPassword, newPassword)
        val result = safeApiCall { authApi.updatePassword(updatePasswordRequest) }
        if (result is ApiResult.Success) {
            tokenStore?.save(AuthTokens(result.data.accessToken, result.data.refreshToken))
        }
        return result
    }

    override suspend fun logout(): ApiResult<Unit> {
        val result = safeApiCallNoContent { authApi.logout() }
        tokenStore?.clear()
        userPreferences.clearAuthData()
        return result
    }

}
