package com.revio.app.data.repository

import com.revio.app.data.remote.api.CarModelApi
import com.revio.app.data.remote.dto.car_model.CarModelOption
import com.revio.app.data.model.CarModel
import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.safeApiCall
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface CarModelRepository {
    suspend fun getAllCarModels(): ApiResult<List<CarModel>>
    suspend fun getCarModelById(modelId: UUID): ApiResult<CarModel>
    suspend fun getAllCarBrands(): ApiResult<List<String>>
    suspend fun getModelsForBrand(brand: String): ApiResult<List<CarModelOption>>
}
@Singleton
class CarModelRepositoryImpl @Inject constructor(
    private val carModelApi: CarModelApi
) : CarModelRepository {

    override suspend fun getAllCarModels(): ApiResult<List<CarModel>> {
        return when(val result = safeApiCall { carModelApi.getAllCarModels()}) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getCarModelById(modelId: UUID): ApiResult<CarModel> {
        return when(val result = safeApiCall { carModelApi.getCarModelById(modelId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Error -> ApiResult.Error(result.message)
        }
    }

    override suspend fun getAllCarBrands(): ApiResult<List<String>> {
        return safeApiCall { carModelApi.getAllCarBrands() }
    }

    override suspend fun getModelsForBrand(brand: String): ApiResult<List<CarModelOption>> {
        return safeApiCall { carModelApi.getModelsForBrand(brand) }
    }
}
