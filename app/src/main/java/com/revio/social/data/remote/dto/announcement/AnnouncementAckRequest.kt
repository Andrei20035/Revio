package com.revio.social.data.remote.dto.announcement

import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementAckRequest(
    val key: String,
)
