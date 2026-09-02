package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ErrorPolicy
import com.revio.social.data.remote.api.NotificationApi
import com.revio.social.data.remote.dto.notification.NotificationCategory
import com.revio.social.data.remote.dto.notification.NotificationListResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response
import java.util.UUID

/** Pas 1.7c: markRead/markAllRead are best-effort — a failure must never be reported. */
class NotificationRepositoryImplTest {

    @Test
    fun `markRead is tagged SILENT on failure`() = runTest {
        val notificationApi: NotificationApi = mockk()
        coEvery { notificationApi.markRead(any()) } throws RuntimeException("boom")
        val repo = NotificationRepositoryImpl(notificationApi)

        val result = repo.markRead(UUID.randomUUID())

        assertEquals(ErrorPolicy.SILENT, (result as ApiResult.Error).policy)
    }

    @Test
    fun `markAllRead is tagged SILENT on failure`() = runTest {
        val notificationApi: NotificationApi = mockk()
        coEvery { notificationApi.markAllRead(any()) } throws RuntimeException("boom")
        val repo = NotificationRepositoryImpl(notificationApi)

        val result = repo.markAllRead()

        assertEquals(ErrorPolicy.SILENT, (result as ApiResult.Error).policy)
    }

    @Test
    fun `getNotifications with category ACCOUNT sends the category query param`() = runTest {
        val notificationApi: NotificationApi = mockk()
        coEvery { notificationApi.getNotifications(any(), any(), any(), any()) } returns
            Response.success(NotificationListResponseDto(unreadCount = 0, items = emptyList()))
        val repo = NotificationRepositoryImpl(notificationApi)

        repo.getNotifications(category = NotificationCategory.ACCOUNT)

        coVerify { notificationApi.getNotifications(50, null, null, "ACCOUNT") }
    }

    @Test
    fun `getNotifications without a category does not send the category query param`() = runTest {
        val notificationApi: NotificationApi = mockk()
        coEvery { notificationApi.getNotifications(any(), any(), any(), any()) } returns
            Response.success(NotificationListResponseDto(unreadCount = 0, items = emptyList()))
        val repo = NotificationRepositoryImpl(notificationApi)

        repo.getNotifications()

        coVerify { notificationApi.getNotifications(50, null, null, null) }
    }

    @Test
    fun `markAllRead with category ACCOUNT sends the category query param`() = runTest {
        val notificationApi: NotificationApi = mockk()
        coEvery { notificationApi.markAllRead(any()) } returns Response.success(Unit)
        val repo = NotificationRepositoryImpl(notificationApi)

        repo.markAllRead(category = NotificationCategory.ACCOUNT)

        coVerify { notificationApi.markAllRead("ACCOUNT") }
    }

    @Test
    fun `a response with nextCursor and hasMore deserializes correctly`() {
        val json = Json { ignoreUnknownKeys = true }

        val dto = json.decodeFromString<NotificationListResponseDto>(
            """
            {
                "unreadCount": 3,
                "items": [],
                "nextCursor": {
                    "lastCreatedAt": "2026-08-25T10:00:00Z",
                    "lastNotificationId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                },
                "hasMore": true
            }
            """.trimIndent(),
        )

        assertEquals(true, dto.hasMore)
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", dto.nextCursor?.lastNotificationId.toString())
    }

    @Test
    fun `a response without nextCursor still deserializes with defaults`() {
        val json = Json { ignoreUnknownKeys = true }

        val dto = json.decodeFromString<NotificationListResponseDto>(
            """
            {
                "unreadCount": 0,
                "items": []
            }
            """.trimIndent(),
        )

        assertNull(dto.nextCursor)
        assertEquals(false, dto.hasMore)
    }
}
