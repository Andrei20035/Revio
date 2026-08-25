package com.revio.social.data.remote.api

import com.revio.social.data.remote.dto.notification.NotificationPrefsDto
import com.revio.social.data.remote.dto.notification.UpdateNotificationPrefsRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface NotificationPrefsApi {
    @GET("users/me/notification-preferences")
    suspend fun getPreferences(): Response<NotificationPrefsDto>

    @PUT("users/me/notification-preferences")
    suspend fun updatePreferences(@Body request: UpdateNotificationPrefsRequest): Response<NotificationPrefsDto>
}
