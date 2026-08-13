package com.revio.social.data.remote.api

import com.revio.social.data.remote.dto.admin.car_family.CarFamilyAdminDto
import com.revio.social.data.remote.dto.car_model.CarModelOption
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.UUID

interface AdminCarFamilyApi {

    @GET("admin/car-families")
    suspend fun listFamilies(): Response<List<CarFamilyAdminDto>>

    /** Every car_models row currently linked to [familyId] — the create-challenge wizard's
     * "included models" preview. */
    @GET("admin/car-families/{id}/models")
    suspend fun listModelsForFamily(@Path("id") familyId: UUID): Response<List<CarModelOption>>
}
