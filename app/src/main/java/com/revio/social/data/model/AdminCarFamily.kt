package com.revio.social.data.model

import com.revio.social.data.remote.dto.admin.car_family.CarFamilyAdminDto
import java.util.UUID

/** Domain model for an admin-manageable car family. Mirrors the server's `CarFamilyAdminDTO`. */
data class AdminCarFamily(
    val id: UUID,
    val brand: String,
    val name: String,
)

fun CarFamilyAdminDto.toDomain(): AdminCarFamily = AdminCarFamily(
    id = id,
    brand = brand,
    name = name,
)
