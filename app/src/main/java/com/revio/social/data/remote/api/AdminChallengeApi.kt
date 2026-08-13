package com.revio.social.data.remote.api

import com.revio.social.data.remote.dto.admin.challenge.ChallengeAdminDto
import com.revio.social.data.remote.dto.admin.challenge.ChallengeAdminPageDto
import com.revio.social.data.remote.dto.admin.challenge.CreateChallengeAdminRequestDto
import com.revio.social.data.remote.dto.admin.challenge.FinalizationResultDto
import com.revio.social.data.remote.dto.admin.challenge.RevokeAllRequestDto
import com.revio.social.data.remote.dto.admin.challenge.RevokeResultDto
import com.revio.social.data.remote.dto.admin.challenge.UpdateChallengeTitleRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID

interface AdminChallengeApi {

    @POST("admin/challenges")
    suspend fun createChallenge(@Body request: CreateChallengeAdminRequestDto): Response<ChallengeAdminDto>

    /** Keyset-paginated list, filterable by effective status (DRAFT/SCHEDULED/ACTIVE/ENDED/CANCELLED). */
    @GET("admin/challenges")
    suspend fun listChallenges(
        @Query("limit") limit: Int? = null,
        @Query("cursorCreatedAt") cursorCreatedAt: String? = null,
        @Query("cursorId") cursorId: String? = null,
        @Query("status") status: String? = null,
    ): Response<ChallengeAdminPageDto>

    @GET("admin/challenges/{id}")
    suspend fun getChallenge(@Path("id") challengeId: UUID): Response<ChallengeAdminDto>

    /** Full-field replace of a DRAFT challenge. */
    @PUT("admin/challenges/{id}")
    suspend fun updateChallenge(
        @Path("id") challengeId: UUID,
        @Body request: CreateChallengeAdminRequestDto,
    ): Response<ChallengeAdminDto>

    /** Title/description-only edit, allowed after publish too. */
    @PATCH("admin/challenges/{id}")
    suspend fun updateChallengeTitle(
        @Path("id") challengeId: UUID,
        @Body request: UpdateChallengeTitleRequestDto,
    ): Response<ChallengeAdminDto>

    @POST("admin/challenges/{id}/publish")
    suspend fun publishChallenge(@Path("id") challengeId: UUID): Response<ChallengeAdminDto>

    @POST("admin/challenges/{id}/cancel")
    suspend fun cancelChallenge(@Path("id") challengeId: UUID): Response<RevokeResultDto>

    /** [request]'s confirmChallengeId must repeat [challengeId] — server-enforced confirmation barrier. */
    @POST("admin/challenges/{id}/revoke-all")
    suspend fun revokeAllRewards(
        @Path("id") challengeId: UUID,
        @Body request: RevokeAllRequestDto,
    ): Response<RevokeResultDto>

    /** Manual/debug finalize — idempotent, same engine as the cron sweep. */
    @POST("admin/challenges/{id}/finalize")
    suspend fun finalizeChallenge(@Path("id") challengeId: UUID): Response<FinalizationResultDto>
}
