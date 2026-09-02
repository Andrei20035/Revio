package com.revio.social.data.remote.api

import com.revio.social.data.remote.dto.notification.NotificationListResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID

interface NotificationApi {
    @GET("notifications")
    suspend fun getNotifications(
        @Query("limit") limit: Int = 50,
        @Query("cursorCreatedAt") cursorCreatedAt: String? = null,
        @Query("cursorNotificationId") cursorNotificationId: String? = null,
        @Query("category") category: String? = null,
    ): Response<NotificationListResponseDto>

    @POST("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: UUID): Response<Unit>

    @POST("notifications/read-all")
    suspend fun markAllRead(@Query("category") category: String? = null): Response<Unit>
}
