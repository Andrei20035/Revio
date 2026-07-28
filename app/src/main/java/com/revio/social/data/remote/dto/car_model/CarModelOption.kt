package com.revio.social.data.remote.dto.car_model

import com.revio.social.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CarModelOption(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val model: String
)
