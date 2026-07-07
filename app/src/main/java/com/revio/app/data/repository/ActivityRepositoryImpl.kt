package com.revio.app.data.repository

import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.safeApiCall
import com.revio.app.data.remote.api.ActivityApi
import com.revio.app.data.remote.dto.activity.toDomain
import com.revio.app.features.activity.model.ActivityData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    private val activityApi: ActivityApi,
) : ActivityRepository {

    override suspend fun getActivity(limit: Int, timezone: String?): ApiResult<ActivityData> {
        return when (val result = safeApiCall { activityApi.getActivity(limit, timezone) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }
}
