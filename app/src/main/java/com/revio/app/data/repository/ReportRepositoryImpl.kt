package com.revio.app.data.repository

import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.safeApiCall
import com.revio.app.data.model.ReportReason
import com.revio.app.data.remote.api.ReportApi
import com.revio.app.data.remote.dto.report.ReportRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface ReportRepository {
    suspend fun reportPost(postId: UUID, reason: ReportReason): ApiResult<Unit>
}

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val reportApi: ReportApi,
) : ReportRepository {

    override suspend fun reportPost(postId: UUID, reason: ReportReason): ApiResult<Unit> {
        return safeApiCall { reportApi.reportPost(postId, ReportRequest(reason)) }
    }
}
