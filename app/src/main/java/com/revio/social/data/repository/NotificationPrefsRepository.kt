package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.safeApiCall
import com.revio.social.data.remote.api.NotificationPrefsApi
import com.revio.social.data.remote.dto.notification.NotificationPrefsDto
import com.revio.social.data.remote.dto.notification.UpdateNotificationPrefsRequest
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationPrefsRepository {
    suspend fun getPreferences(): ApiResult<NotificationPrefsDto>
    suspend fun updatePreferences(request: UpdateNotificationPrefsRequest): ApiResult<NotificationPrefsDto>
}

@Singleton
class NotificationPrefsRepositoryImpl @Inject constructor(
    private val notificationPrefsApi: NotificationPrefsApi,
) : NotificationPrefsRepository {

    override suspend fun getPreferences(): ApiResult<NotificationPrefsDto> =
        safeApiCall { notificationPrefsApi.getPreferences() }

    override suspend fun updatePreferences(request: UpdateNotificationPrefsRequest): ApiResult<NotificationPrefsDto> =
        safeApiCall { notificationPrefsApi.updatePreferences(request) }
}
