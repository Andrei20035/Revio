package com.revio.social.data.remote.dto.user_car

import com.revio.social.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserCarUpdateRequest(
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID,
    val imagePath: String? = null,
)