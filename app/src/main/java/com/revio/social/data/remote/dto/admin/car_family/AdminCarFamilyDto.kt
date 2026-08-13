package com.revio.social.data.remote.dto.admin.car_family

import com.revio.social.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/** Response element of `GET /admin/car-families`. Mirrors the server's `CarFamilyAdminDTO`. */
@Serializable
data class CarFamilyAdminDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val brand: String,
    val name: String,
)
