package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ErrorPolicy
import com.revio.social.core.network.safeApiCall
import com.revio.social.core.network.safeApiCallNoContent
import com.revio.social.data.remote.api.NotificationApi
import com.revio.social.data.remote.dto.notification.NotificationListResponseDto
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationRepository {
    suspend fun getNotifications(limit: Int = 50): ApiResult<NotificationListResponseDto>
    suspend fun markRead(id: UUID): ApiResult<Unit>
    suspend fun markAllRead(): ApiResult<Unit>
}

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationApi: NotificationApi,
) : NotificationRepository {

    override suspend fun getNotifications(limit: Int): ApiResult<NotificationListResponseDto> =
        safeApiCall { notificationApi.getNotifications(limit) }

    override suspend fun markRead(id: UUID): ApiResult<Unit> =
        safeApiCallNoContent(policy = ErrorPolicy.SILENT) { notificationApi.markRead(id) }

    override suspend fun markAllRead(): ApiResult<Unit> =
        safeApiCallNoContent(policy = ErrorPolicy.SILENT) { notificationApi.markAllRead() }
}
