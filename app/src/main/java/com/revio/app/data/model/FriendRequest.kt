package com.revio.app.data.model

import com.revio.app.core.network.serialization.InstantSerializer
import com.revio.app.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class FriendRequest(
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val receiverId: UUID,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant? = null,
)
