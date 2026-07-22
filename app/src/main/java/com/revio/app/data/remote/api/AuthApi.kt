package com.revio.app.data.remote.api

import com.revio.app.data.remote.dto.auth.AuthResponse
import com.revio.app.data.remote.dto.auth.DeleteAccountRequest
import com.revio.app.data.remote.dto.auth.DeletionContextDto
import com.revio.app.data.remote.dto.auth.LoginRequest
import com.revio.app.data.remote.dto.auth.RefreshRequest
import com.revio.app.data.remote.dto.auth.RegisterRequest
import com.revio.app.data.remote.dto.auth.UpdatePasswordRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT


interface AuthApi {

    @POST("auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body refreshRequest: RefreshRequest): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @HTTP(method = "DELETE", path = "auth/account", hasBody = true)
    suspend fun deleteAccount(@Body deleteAccountRequest: DeleteAccountRequest): Response<Unit>

    @GET("auth/account/deletion-context")
    suspend fun getDeletionContext(): Response<DeletionContextDto>

    @PUT("auth/password")
    suspend fun updatePassword(@Body updatePasswordRequest: UpdatePasswordRequest): Response<AuthResponse>
}
