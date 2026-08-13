package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.map
import com.revio.social.core.network.safeApiCall
import com.revio.social.data.model.AdminCarFamily
import com.revio.social.data.model.toDomain
import com.revio.social.data.remote.api.AdminCarFamilyApi
import com.revio.social.data.remote.dto.car_model.CarModelOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface AdminCarFamilyRepository {
    suspend fun listFamilies(): ApiResult<List<AdminCarFamily>>

    suspend fun listModelsForFamily(familyId: UUID): ApiResult<List<CarModelOption>>
}

@Singleton
class AdminCarFamilyRepositoryImpl @Inject constructor(
    private val adminCarFamilyApi: AdminCarFamilyApi,
) : AdminCarFamilyRepository {

    override suspend fun listFamilies(): ApiResult<List<AdminCarFamily>> =
        safeApiCall { adminCarFamilyApi.listFamilies() }.map { families -> families.map { it.toDomain() } }

    override suspend fun listModelsForFamily(familyId: UUID): ApiResult<List<CarModelOption>> =
        safeApiCall { adminCarFamilyApi.listModelsForFamily(familyId) }
}
