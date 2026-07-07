package com.revio.app.data.remote.api

import com.revio.app.data.remote.dto.activity.ActivityResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ActivityApi {
    @GET("activity")
    suspend fun getActivity(
        @Query("limit") limit: Int = 50,
        @Query("timezone") timezone: String? = null,
    ): Response<ActivityResponseDto>
}
