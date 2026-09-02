package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ErrorPolicy
import com.revio.social.core.network.safeApiCall
import com.revio.social.core.network.safeApiCallNoContent
import com.revio.social.data.remote.api.NotificationApi
import com.revio.social.data.remote.dto.notification.NotificationCategory
import com.revio.social.data.remote.dto.notification.NotificationListResponseDto
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationRepository {
    suspend fun getNotifications(
        limit: Int = 50,
        cursorCreatedAt: String? = null,
        cursorNotificationId: String? = null,
        category: NotificationCategory? = null,
    ): ApiResult<NotificationListResponseDto>
    suspend fun markRead(id: UUID): ApiResult<Unit>
    suspend fun markAllRead(category: NotificationCategory? = null): ApiResult<Unit>
}

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationApi: NotificationApi,
) : NotificationRepository {

    override suspend fun getNotifications(
        limit: Int,
        cursorCreatedAt: String?,
        cursorNotificationId: String?,
        category: NotificationCategory?,
    ): ApiResult<NotificationListResponseDto> =
        safeApiCall { notificationApi.getNotifications(limit, cursorCreatedAt, cursorNotificationId, category?.name) }

    override suspend fun markRead(id: UUID): ApiResult<Unit> =
        safeApiCallNoContent(policy = ErrorPolicy.SILENT) { notificationApi.markRead(id) }

    override suspend fun markAllRead(category: NotificationCategory?): ApiResult<Unit> =
        safeApiCallNoContent(policy = ErrorPolicy.SILENT) { notificationApi.markAllRead(category?.name) }
}
