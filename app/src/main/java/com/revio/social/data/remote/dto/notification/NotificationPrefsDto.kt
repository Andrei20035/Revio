package com.revio.social.data.remote.dto.notification

import kotlinx.serialization.Serializable

/**
 * Mirrors the server's `NotificationPrefsDTO`. Quiet-hours fields exist server-side but aren't
 * read or written here (`ignoreUnknownKeys` on decode, and `UpdateNotificationPrefsRequest`'s
 * fields are all independently optional server-side, so omitting them leaves them unchanged).
 */
@Serializable
data class NotificationPrefsDto(
    val likesEnabled: Boolean,
    val commentsEnabled: Boolean,
    val discoveryEnabled: Boolean,
    val remindersEnabled: Boolean,
)

@Serializable
data class UpdateNotificationPrefsRequest(
    val likesEnabled: Boolean? = null,
    val commentsEnabled: Boolean? = null,
    val discoveryEnabled: Boolean? = null,
    val remindersEnabled: Boolean? = null,
)
