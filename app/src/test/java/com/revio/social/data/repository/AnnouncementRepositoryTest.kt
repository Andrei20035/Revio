package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ErrorPolicy
import com.revio.social.data.remote.api.AnnouncementApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pas 1.7c: getPending/acknowledge are best-effort — a failure must never be reported. */
class AnnouncementRepositoryTest {

    @Test
    fun `getPending is tagged SILENT on failure`() = runTest {
        val announcementApi: AnnouncementApi = mockk()
        coEvery { announcementApi.getPendingAnnouncements() } throws RuntimeException("boom")
        val repo = AnnouncementRepositoryImpl(announcementApi)

        val result = repo.getPending()

        assertEquals(ErrorPolicy.SILENT, (result as ApiResult.Error).policy)
    }

    @Test
    fun `acknowledge is tagged SILENT on failure`() = runTest {
        val announcementApi: AnnouncementApi = mockk()
        coEvery { announcementApi.acknowledgeAnnouncement(any()) } throws RuntimeException("boom")
        val repo = AnnouncementRepositoryImpl(announcementApi)

        val result = repo.acknowledge("EARLY_SPOTTER_WELCOME")

        assertEquals(ErrorPolicy.SILENT, (result as ApiResult.Error).policy)
    }
}
