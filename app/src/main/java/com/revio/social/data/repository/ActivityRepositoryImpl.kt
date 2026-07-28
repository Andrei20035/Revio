package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.safeApiCall
import com.revio.social.data.remote.api.ActivityApi
import com.revio.social.data.remote.dto.activity.toDomain
import com.revio.social.features.activity.model.ActivityData
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
