package com.revio.app.data.remote.api

import com.revio.app.data.remote.dto.auth.AuthResponse
import com.revio.app.data.remote.dto.auth.LoginRequest
import com.revio.app.data.remote.dto.auth.RefreshRequest
import com.revio.app.data.remote.dto.auth.RegisterRequest
import com.revio.app.data.remote.dto.auth.UpdatePasswordRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
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

    @DELETE("auth/account")
    suspend fun deleteAccount(): Response<Unit>

    @PUT("auth/password")
    suspend fun updatePassword(@Body updatePasswordRequest: UpdatePasswordRequest): Response<AuthResponse>
}
