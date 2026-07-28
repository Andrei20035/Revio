package com.revio.social.data.remote.dto.post

import com.revio.social.core.network.serialization.InstantSerializer
import com.revio.social.core.network.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable


@Serializable
data class FeedCursor(
    @Serializable(with = InstantSerializer::class)
    val lastCreatedAt: Instant,
    @Serializable(with = UUIDSerializer::class)
    val lastPostId: UUID
)