package com.revio.social.data.repository

import com.revio.social.data.remote.api.FriendRequestApi
import com.revio.social.data.model.FriendRequest
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.safeApiCall
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface FriendRequestRepository {
    suspend fun getAllFriendRequests(): ApiResult<List<FriendRequest>>
    suspend fun getAllFriendRequestsAdmin(): ApiResult<List<FriendRequest>>
    suspend fun sendFriendRequest(receiverId: UUID): ApiResult<Unit>
    suspend fun acceptFriendRequest(senderId: UUID): ApiResult<Unit>
    suspend fun declineFriendRequest(senderId: UUID): ApiResult<Unit>
}
@Singleton
class FriendRequestRepositoryImpl @Inject constructor(
    private val friendRequestApi: FriendRequestApi
) : FriendRequestRepository {

    override suspend fun getAllFriendRequests(): ApiResult<List<FriendRequest>> {
        return when (val result = safeApiCall { friendRequestApi.getAllFriendRequests()}) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getAllFriendRequestsAdmin(): ApiResult<List<FriendRequest>> {
        return when (val result = safeApiCall { friendRequestApi.getAllFriendRequestsAdmin()}) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun sendFriendRequest(receiverId: UUID): ApiResult<Unit> {
        return safeApiCall { friendRequestApi.sendFriendRequest(receiverId)}
    }

    override suspend fun acceptFriendRequest(senderId: UUID): ApiResult<Unit> {
        return safeApiCall { friendRequestApi.acceptFriendRequest(senderId)}
    }

    override suspend fun declineFriendRequest(senderId: UUID): ApiResult<Unit> {
        return safeApiCall { friendRequestApi.declineFriendRequest(senderId)}
    }
}
