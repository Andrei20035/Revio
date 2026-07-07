package com.revio.app.data.repository

import com.revio.app.data.remote.api.UserCarApi
import com.revio.app.data.remote.dto.user_car.UserCarRequest
import com.revio.app.data.remote.dto.user_car.UserCarUpdateRequest
import com.revio.app.data.model.User
import com.revio.app.data.model.UserCar
import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface UserCarRepository {
    suspend fun createUserCar(request: UserCarRequest): ApiResult<Unit>
    suspend fun createMyCar(request: UserCarRequest, imageBytes: ByteArray, mimeType: String): ApiResult<UserCar>
    suspend fun getUserCarById(userCarId: UUID): ApiResult<UserCar>
    suspend fun getUserCarByUserId(userId: UUID): ApiResult<UserCar>
    suspend fun getUserByUserCarId(userCarId: UUID): ApiResult<User>
    suspend fun updateUserCar(request: UserCarUpdateRequest): ApiResult<Unit>
    suspend fun updateMyCar(request: UserCarUpdateRequest?, imageBytes: ByteArray?, mimeType: String?): ApiResult<UserCar>
    suspend fun deleteUserCar(): ApiResult<Unit>
    suspend fun getAllUserCars(): ApiResult<List<UserCar>>
}
@Singleton
class UserCarRepositoryImpl @Inject constructor(
    private val userCarApi: UserCarApi
) : UserCarRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createUserCar(request: UserCarRequest): ApiResult<Unit> {
        return ApiResult.Error("Legacy user car creation is not supported by the current server")
    }

    override suspend fun createMyCar(
        request: UserCarRequest,
        imageBytes: ByteArray,
        mimeType: String
    ): ApiResult<UserCar> {
        val metadata = json.encodeToString(request)
            .toRequestBody("application/json".toMediaType())
        val image = MultipartBody.Part.createFormData(
            name = "image",
            filename = "car.jpg",
            body = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )

        return safeApiCall { userCarApi.createMyCar(metadata, image) }
    }

    override suspend fun getUserCarById(userCarId: UUID): ApiResult<UserCar> {
        return when (val result = safeApiCall { userCarApi.getUserCarById(userCarId)}) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getUserCarByUserId(userId: UUID): ApiResult<UserCar> {
        return when (val result = safeApiCall { userCarApi.getUserCarByUserId(userId)}) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getUserByUserCarId(userCarId: UUID): ApiResult<User> {
        return when (val result = safeApiCall { userCarApi.getUserByUserCarId(userCarId)}) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun updateUserCar(request: UserCarUpdateRequest): ApiResult<Unit> {
        return ApiResult.Error("Legacy user car update is not supported by the current server")
    }

    override suspend fun updateMyCar(
        request: UserCarUpdateRequest?,
        imageBytes: ByteArray?,
        mimeType: String?
    ): ApiResult<UserCar> {
        val metadata = request?.let {
            json.encodeToString(it).toRequestBody("application/json".toMediaType())
        }
        val image = imageBytes?.let {
            MultipartBody.Part.createFormData(
                name = "image",
                filename = "car.jpg",
                body = it.toRequestBody((mimeType ?: "image/jpeg").toMediaTypeOrNull())
            )
        }

        return safeApiCall { userCarApi.updateMyCar(metadata, image) }
    }

    override suspend fun deleteUserCar(): ApiResult<Unit> {
        return safeApiCall { userCarApi.deleteUserCar()}
    }

    override suspend fun getAllUserCars(): ApiResult<List<UserCar>> {
        return when (val result = safeApiCall { userCarApi.getAllUserCars()}) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }
}
