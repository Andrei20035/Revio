package com.revio.social.data.remote.dto.announcement

import kotlinx.serialization.Serializable

/** Mirrors the server's AnnouncementDTO — one PENDING/SEEN announcement (e.g. Early Spotter welcome/bonus). */
@Serializable
data class AnnouncementDTO(
    val key: String,
    val status: String,
    val payload: String? = null,
)
