package com.revio.app.data.repository

import com.revio.app.data.remote.api.FriendApi
import com.revio.app.data.model.Friend
import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.safeApiCall
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface FriendRepository {
    suspend fun getAllFriends(): ApiResult<List<Friend>>
    suspend fun getAllFriendsAdmin(): ApiResult<List<Friend>>
    suspend fun addFriend(friendId: UUID): ApiResult<Unit>
    suspend fun deleteFriend(friendId: UUID): ApiResult<Unit>
}
@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val friendApi: FriendApi
) : FriendRepository {

    override suspend fun getAllFriends(): ApiResult<List<Friend>> {
        return when (val result = safeApiCall { friendApi.getAllFriends()}) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getAllFriendsAdmin(): ApiResult<List<Friend>> {
        return when (val result = safeApiCall { friendApi.getAllFriendsAdmin()}) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun addFriend(friendId: UUID): ApiResult<Unit> {
        return safeApiCall { friendApi.addFriend(friendId)}
    }

    override suspend fun deleteFriend(friendId: UUID): ApiResult<Unit> {
        return safeApiCall { friendApi.deleteFriend(friendId)}
    }
}
