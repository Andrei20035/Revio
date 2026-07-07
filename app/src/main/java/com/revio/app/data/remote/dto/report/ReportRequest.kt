package com.revio.app.data.remote.dto.report

import com.revio.app.data.model.ReportReason
import kotlinx.serialization.Serializable

/**
 * Body for `POST posts/{postId}/reports`. The post id travels in the path and the
 * reporter is derived from the auth token server-side, so only the reason is sent.
 */
@Serializable
data class ReportRequest(
    val reason: ReportReason,
)
