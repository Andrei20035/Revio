package com.revio.app.data.remote.api

import com.revio.app.data.model.User
import com.revio.app.data.remote.dto.user.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*
import java.util.UUID

interface UserApi {

    @GET("users/{userId}")
    suspend fun getUserById(
        @Path("userId") userId: UUID
    ): Response<User>

    @GET("users/me")
    suspend fun getCurrentUser(): Response<User>

    @GET("users")
    suspend fun getAllUsers(): Response<List<User>>

    @GET("users/by-username/{username}")
    suspend fun getUsersByUsername(
        @Path("username") username: String
    ): Response<List<User>>

    @POST("users")
    suspend fun createUser(
        @Body createUserRequest: CreateUserRequest
    ): Response<CreateUserResponse>

    @PATCH("users/me")
    suspend fun updateUser(
        @Body updateUserRequest: UpdateUserRequest
    ): Response<User>

    @PATCH("users/me/profile-picture")
    suspend fun updateProfilePicture(
        @Body updateProfilePictureRequest: UpdateProfilePictureRequest
    ): Response<User>

    @Multipart
    @PATCH("users/me/profile-picture")
    suspend fun uploadProfilePicture(
        @Part image: MultipartBody.Part
    ): Response<User>

    @DELETE("users/me")
    suspend fun deleteCurrentUser(): Response<Unit>
}
