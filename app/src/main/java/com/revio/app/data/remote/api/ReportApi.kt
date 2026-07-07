package com.revio.app.data.remote.api

import com.revio.app.data.remote.dto.report.ReportRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.UUID

interface ReportApi {

    /**
     * Report a post for a typed reason. Returns 201 on a new report and 200 when the
     * user had already filed the same report — both are treated as success by the repo.
     */
    @POST("posts/{postId}/reports")
    suspend fun reportPost(
        @Path("postId") postId: UUID,
        @Body request: ReportRequest,
    ): Response<Unit>
}
