package com.revio.app.data.repository

import com.revio.app.data.remote.api.UserApi
import com.revio.app.data.remote.dto.user.*
import com.revio.app.data.model.User
import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    val currentUser: StateFlow<User?>
    suspend fun getUserById(userId: UUID): ApiResult<User>
    suspend fun getCurrentUser(): ApiResult<User>
    suspend fun getAllUsers(): ApiResult<List<User>>
    suspend fun getUsersByUsername(username: String): ApiResult<List<User>>
    suspend fun checkUsernameAvailability(username: String): ApiResult<UsernameAvailabilityResponse>
    suspend fun createUser(request: CreateUserRequest): ApiResult<CreateUserResponse>
    suspend fun updateUser(request: UpdateUserRequest): ApiResult<User>
    suspend fun updateProfilePicture(request: UpdateProfilePictureRequest): ApiResult<User>
    suspend fun uploadProfilePicture(imageBytes: ByteArray, mimeType: String): ApiResult<User>
    suspend fun deleteCurrentUser(): ApiResult<Unit>
}
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApi: UserApi
) : UserRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    override suspend fun getUserById(userId: UUID): ApiResult<User> {
        return when (val result = safeApiCall { userApi.getUserById(userId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getCurrentUser(): ApiResult<User> {
        return when (val result = safeApiCall { userApi.getCurrentUser()}) {
            is ApiResult.Success -> {
                _currentUser.value = result.data
                ApiResult.Success(result.data)
            }
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getAllUsers(): ApiResult<List<User>> {
        return when (val result = safeApiCall { userApi.getAllUsers()}) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getUsersByUsername(username: String): ApiResult<List<User>> {
        return try {
            val response = userApi.getUsersByUsername(username)
            when {
                response.isSuccessful -> ApiResult.Success(response.body().orEmpty())
                response.code() == USER_NOT_FOUND_STATUS -> ApiResult.Success(emptyList())
                else -> ApiResult.Error(
                    response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                        ?: "Failed to check username availability"
                )
            }
        } catch (exception: Exception) {
            ApiResult.Error(exception.message ?: "Failed to check username availability")
        }
    }

    override suspend fun checkUsernameAvailability(username: String): ApiResult<UsernameAvailabilityResponse> {
        return safeApiCall { userApi.checkUsernameAvailability(username) }
    }

    override suspend fun createUser(request: CreateUserRequest): ApiResult<CreateUserResponse> {
        return safeApiCall { userApi.createUser(request)}
    }

    override suspend fun updateUser(request: UpdateUserRequest): ApiResult<User> {
        return safeApiCall { userApi.updateUser(request) }.also { result ->
            if (result is ApiResult.Success) _currentUser.value = result.data
        }
    }

    override suspend fun updateProfilePicture(request: UpdateProfilePictureRequest): ApiResult<User> {
        return safeApiCall { userApi.updateProfilePicture(request)}.also { result ->
            if (result is ApiResult.Success) _currentUser.value = result.data
        }
    }

    override suspend fun uploadProfilePicture(
        imageBytes: ByteArray,
        mimeType: String
    ): ApiResult<User> {
        val image = MultipartBody.Part.createFormData(
            name = "image",
            filename = "profile.jpg",
            body = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )

        return safeApiCall { userApi.uploadProfilePicture(image) }.also { result ->
            if (result is ApiResult.Success) _currentUser.value = result.data
        }
    }

    override suspend fun deleteCurrentUser(): ApiResult<Unit> {
        return safeApiCall { userApi.deleteCurrentUser()}.also { result ->
            if (result is ApiResult.Success) _currentUser.value = null
        }
    }

    private companion object {
        const val USER_NOT_FOUND_STATUS = 404
    }
}
