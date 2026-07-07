package com.revio.app.data.repository

import com.revio.app.core.network.ApiResult
import com.revio.app.features.activity.model.ActivityData

interface ActivityRepository {
    suspend fun getActivity(limit: Int = 50, timezone: String? = null): ApiResult<ActivityData>
}
