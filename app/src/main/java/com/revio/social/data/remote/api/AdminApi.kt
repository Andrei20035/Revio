package com.revio.social.data.remote.api

import com.revio.social.data.remote.dto.admin.AdminUserSummaryDto
import com.revio.social.data.remote.dto.admin.BanUserRequestDto
import com.revio.social.data.remote.dto.admin.RemovePostRequestDto
import com.revio.social.data.remote.dto.admin.RemovePostResponseDto
import com.revio.social.data.remote.dto.admin.ReportAdminDto
import com.revio.social.data.remote.dto.admin.ResolveReportRequestDto
import com.revio.social.data.remote.dto.admin.UserModerationResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID

interface AdminApi {
    @GET("admin/reports")
    suspend fun listReports(@Query("status") status: String? = null): Response<List<ReportAdminDto>>

    @POST("admin/reports/{id}/resolve")
    suspend fun resolveReport(
        @Path("id") id: UUID,
        @Body request: ResolveReportRequestDto,
    ): Response<ReportAdminDto>

    @POST("admin/posts/{postId}/remove")
    suspend fun removePost(
        @Path("postId") postId: UUID,
        @Body request: RemovePostRequestDto,
    ): Response<RemovePostResponseDto>

    @GET("admin/users")
    suspend fun searchUsers(@Query("query") query: String): Response<List<AdminUserSummaryDto>>

    @GET("admin/users/{userId}/moderation")
    suspend fun getUserModeration(@Path("userId") userId: UUID): Response<UserModerationResponseDto>

    @POST("admin/users/{userId}/ban")
    suspend fun banUser(
        @Path("userId") userId: UUID,
        @Body request: BanUserRequestDto,
    ): Response<Unit>

    @POST("admin/users/{userId}/unban")
    suspend fun unbanUser(@Path("userId") userId: UUID): Response<Unit>

    @POST("admin/violations/{id}/revoke")
    suspend fun revokeViolation(@Path("id") id: UUID): Response<Unit>
}
