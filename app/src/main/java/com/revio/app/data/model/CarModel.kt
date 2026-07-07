package com.revio.app.data.model

import com.revio.app.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CarModel(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val brand: String,
    val model: String,
    val year: Int? = null,
)
