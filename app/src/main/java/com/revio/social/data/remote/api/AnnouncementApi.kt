package com.revio.social.data.remote.api

import com.revio.social.data.remote.dto.announcement.AnnouncementAckRequest
import com.revio.social.data.remote.dto.announcement.AnnouncementDTO
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AnnouncementApi {

    /** PENDING announcements for the current user — the recovery path after restart/relogin/another device. */
    @GET("users/me/announcements")
    suspend fun getPendingAnnouncements(): Response<List<AnnouncementDTO>>

    /** Idempotent: acknowledging an already-SEEN or unknown-to-this-user key still returns 200. */
    @POST("users/me/announcements/ack")
    suspend fun acknowledgeAnnouncement(
        @Body request: AnnouncementAckRequest,
    ): Response<Unit>
}
