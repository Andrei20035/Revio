package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ErrorPolicy
import com.revio.social.core.network.safeApiCall
import com.revio.social.core.network.safeApiCallNoContent
import com.revio.social.data.remote.api.AnnouncementApi
import com.revio.social.data.remote.dto.announcement.AnnouncementAckRequest
import com.revio.social.data.remote.dto.announcement.AnnouncementDTO
import javax.inject.Inject
import javax.inject.Singleton

interface AnnouncementRepository {
    /** PENDING announcements for the current user — the recovery path after restart/relogin/another device. */
    suspend fun getPending(): ApiResult<List<AnnouncementDTO>>

    /** Idempotent: acknowledging an already-SEEN or unknown-to-this-user key still succeeds. */
    suspend fun acknowledge(key: String): ApiResult<Unit>
}

@Singleton
class AnnouncementRepositoryImpl @Inject constructor(
    private val announcementApi: AnnouncementApi,
) : AnnouncementRepository {

    override suspend fun getPending(): ApiResult<List<AnnouncementDTO>> =
        safeApiCall(policy = ErrorPolicy.SILENT) { announcementApi.getPendingAnnouncements() }

    override suspend fun acknowledge(key: String): ApiResult<Unit> =
        safeApiCallNoContent(policy = ErrorPolicy.SILENT) { announcementApi.acknowledgeAnnouncement(AnnouncementAckRequest(key)) }
}
