package com.revio.social.data.remote.api

import com.revio.social.data.remote.dto.challenge.ChallengeDto
import com.revio.social.data.remote.dto.challenge.ChallengeProgressDetailDto
import com.revio.social.data.remote.dto.challenge.CurrentChallengeDto
import com.revio.social.data.remote.dto.challenge.MyChallengesDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID

interface ChallengeApi {

    /**
     * The active challenge if one is running, otherwise the soonest upcoming one — the server
     * does not filter to "currently active" (see `ChallengeDAO.findCurrentOrNext`), so callers
     * must check `startsAt <= now < endsAt` themselves before treating [CurrentChallengeDto] as
     * live. Both `challenge` and `progress` are null when nothing is scheduled at all.
     */
    @GET("challenges/current")
    suspend fun getCurrentChallenge(): Response<CurrentChallengeDto>

    @GET("challenges/{id}/progress")
    suspend fun getChallengeProgress(
        @Path("id") challengeId: UUID
    ): Response<ChallengeProgressDetailDto>

    /** The public config of any non-DRAFT challenge. 404 for DRAFT or nonexistent ids. */
    @GET("challenges/{id}")
    suspend fun getChallenge(
        @Path("id") challengeId: UUID
    ): Response<ChallengeDto>

    /**
     * The caller's challenge history, newest-ended-first. [summary] on the response is present
     * only on the first page (no cursor given) — see `MyChallengesDto`'s KDoc. [cursorEndsAt] and
     * [cursorId] must be supplied together or not at all.
     */
    @GET("challenges/me")
    suspend fun getMyChallenges(
        @Query("limit") limit: Int? = null,
        @Query("cursorEndsAt") cursorEndsAt: String? = null,
        @Query("cursorId") cursorId: String? = null,
        @Query("filter") filter: String? = null,
    ): Response<MyChallengesDto>
}
