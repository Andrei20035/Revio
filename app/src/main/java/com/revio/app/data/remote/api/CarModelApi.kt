package com.revio.app.data.remote.api

import com.revio.app.data.model.CarModel
import com.revio.app.data.remote.dto.car_model.CarModelOption
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.UUID

interface CarModelApi {
    @GET("car-models")
    suspend fun getAllCarModels(): Response<List<CarModel>>

    @GET("car-models/{modelId}")
    suspend fun getCarModelById(
        @Path("modelId") modelId: UUID
    ): Response<CarModel>

    @GET("car-models/brands")
    suspend fun getAllCarBrands(): Response<List<String>>

    @GET("car-models/brands/{brand}/models")
    suspend fun getModelsForBrand(
        @Path("brand") brand: String
    ): Response<List<CarModelOption>>
}
