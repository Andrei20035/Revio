package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ErrorPolicy
import com.revio.social.data.remote.api.NotificationApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
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
        coEvery { notificationApi.markAllRead() } throws RuntimeException("boom")
        val repo = NotificationRepositoryImpl(notificationApi)

        val result = repo.markAllRead()

        assertEquals(ErrorPolicy.SILENT, (result as ApiResult.Error).policy)
    }
}
