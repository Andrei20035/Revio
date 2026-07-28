package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.features.activity.model.ActivityData

interface ActivityRepository {
    suspend fun getActivity(limit: Int = 50, timezone: String? = null): ApiResult<ActivityData>
}
