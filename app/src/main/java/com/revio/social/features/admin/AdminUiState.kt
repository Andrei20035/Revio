package com.revio.social.features.admin

import com.revio.social.data.remote.dto.admin.AdminUserSummaryDto
import com.revio.social.data.remote.dto.admin.ReportAdminDto
import com.revio.social.data.remote.dto.admin.UserModerationResponseDto
import java.util.UUID

data class AdminReportsUiState(
    val reports: List<ReportAdminDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
    val resolvingReportId: UUID? = null,
) {
    val isEmpty: Boolean get() = reports.isEmpty()
}

data class AdminUserUiState(
    val query: String = "",
    val searchResults: List<AdminUserSummaryDto> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val selectedUserId: UUID? = null,
    val detail: UserModerationResponseDto? = null,
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null,
    val isMutating: Boolean = false,
    val mutationError: String? = null,
) {
    val isShowingDetail: Boolean get() = selectedUserId != null
}

data class AdminRemovePostUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)
