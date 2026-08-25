package com.revio.social.data.remote.api

import com.revio.social.data.remote.dto.device.DeviceDto
import com.revio.social.data.remote.dto.device.RegisterDeviceRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

interface DeviceApi {
    @POST("devices")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<DeviceDto>

    @DELETE("devices/{deviceId}")
    suspend fun deleteDevice(@Path("deviceId") deviceId: String): Response<Unit>
}
