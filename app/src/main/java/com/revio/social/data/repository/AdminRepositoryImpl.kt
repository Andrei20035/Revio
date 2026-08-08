package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.safeApiCall
import com.revio.social.core.network.safeApiCallNoContent
import com.revio.social.data.model.ModerationReason
import com.revio.social.data.remote.api.AdminApi
import com.revio.social.data.remote.dto.admin.AdminUserSummaryDto
import com.revio.social.data.remote.dto.admin.BanUserRequestDto
import com.revio.social.data.remote.dto.admin.ModerationDecision
import com.revio.social.data.remote.dto.admin.RemovePostRequestDto
import com.revio.social.data.remote.dto.admin.RemovePostResponseDto
import com.revio.social.data.remote.dto.admin.ReportAdminDto
import com.revio.social.data.remote.dto.admin.ReportStatus
import com.revio.social.data.remote.dto.admin.ResolveReportRequestDto
import com.revio.social.data.remote.dto.admin.UserModerationResponseDto
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface AdminRepository {
    suspend fun listReports(status: ReportStatus? = null): ApiResult<List<ReportAdminDto>>
    suspend fun resolveReport(id: UUID, decision: ModerationDecision): ApiResult<ReportAdminDto>
    suspend fun removePost(postId: UUID, reason: ModerationReason, reasonDetails: String?): ApiResult<RemovePostResponseDto>
    suspend fun searchUsers(query: String): ApiResult<List<AdminUserSummaryDto>>
    suspend fun getUserModeration(userId: UUID): ApiResult<UserModerationResponseDto>
    suspend fun banUser(userId: UUID, durationDays: Int?, permanent: Boolean, reason: String?): ApiResult<Unit>
    suspend fun unbanUser(userId: UUID): ApiResult<Unit>
    suspend fun revokeViolation(id: UUID): ApiResult<Unit>
}

@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val adminApi: AdminApi,
) : AdminRepository {

    override suspend fun listReports(status: ReportStatus?): ApiResult<List<ReportAdminDto>> =
        safeApiCall { adminApi.listReports(status?.name) }

    override suspend fun resolveReport(id: UUID, decision: ModerationDecision): ApiResult<ReportAdminDto> =
        safeApiCall { adminApi.resolveReport(id, ResolveReportRequestDto(decision)) }

    override suspend fun removePost(
        postId: UUID,
        reason: ModerationReason,
        reasonDetails: String?,
    ): ApiResult<RemovePostResponseDto> =
        safeApiCall { adminApi.removePost(postId, RemovePostRequestDto(reason, reasonDetails)) }

    override suspend fun searchUsers(query: String): ApiResult<List<AdminUserSummaryDto>> =
        safeApiCall { adminApi.searchUsers(query) }

    override suspend fun getUserModeration(userId: UUID): ApiResult<UserModerationResponseDto> =
        safeApiCall { adminApi.getUserModeration(userId) }

    override suspend fun banUser(
        userId: UUID,
        durationDays: Int?,
        permanent: Boolean,
        reason: String?,
    ): ApiResult<Unit> =
        safeApiCallNoContent { adminApi.banUser(userId, BanUserRequestDto(durationDays, permanent, reason)) }

    override suspend fun unbanUser(userId: UUID): ApiResult<Unit> =
        safeApiCallNoContent { adminApi.unbanUser(userId) }

    override suspend fun revokeViolation(id: UUID): ApiResult<Unit> =
        safeApiCallNoContent { adminApi.revokeViolation(id) }
}
