package com.revio.app.data.remote.api

import com.revio.app.data.model.User
import com.revio.app.data.model.UserCar
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import java.util.UUID

interface UserCarApi {

    @Multipart
    @POST("me/car")
    suspend fun createMyCar(
        @Part("metadata") metadata: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<UserCar>

    @GET("user-cars/{userCarId}")
    suspend fun getUserCarById(
        @Path("userCarId") userCarId: UUID
    ): Response<UserCar>

    @GET("user-cars/by-user/{userId}")
    suspend fun getUserCarByUserId(
        @Path("userId") userId: UUID
    ): Response<UserCar>

    @GET("user-cars/{userCarId}/user")
    suspend fun getUserByUserCarId(
        @Path("userCarId") userCarId: UUID
    ): Response<User>

    @Multipart
    @PATCH("me/car")
    suspend fun updateMyCar(
        @Part("metadata") metadata: RequestBody?,
        @Part image: MultipartBody.Part?
    ): Response<UserCar>

    @DELETE("user-cars")
    suspend fun deleteUserCar(): Response<Unit>

    @GET("user-cars")
    suspend fun getAllUserCars(): Response<List<UserCar>>
}
