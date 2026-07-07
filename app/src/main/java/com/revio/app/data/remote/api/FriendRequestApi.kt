package com.revio.app.data.remote.api

import com.revio.app.data.model.FriendRequest
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.UUID

interface FriendRequestApi {

    @GET("friend-requests/admin")
    suspend fun getAllFriendRequestsAdmin(): Response<List<FriendRequest>>

    @POST("friend-requests/{receiverId}")
    suspend fun sendFriendRequest(
        @Path("receiverId") receiverId: UUID
    ): Response<Unit>

    @POST("friend-requests/{senderId}/accept")
    suspend fun acceptFriendRequest(
        @Path("senderId") senderId: UUID
    ): Response<Unit>

    @POST("friend-requests/{senderId}/decline")
    suspend fun declineFriendRequest(
        @Path("senderId") senderId: UUID
    ): Response<Unit>

    @GET("friend-requests")
    suspend fun getAllFriendRequests(): Response<List<FriendRequest>>
}
