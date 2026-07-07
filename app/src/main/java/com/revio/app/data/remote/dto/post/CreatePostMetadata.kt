package com.revio.app.data.remote.dto.post

import com.revio.app.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * JSON `metadata` part of the multipart `POST /posts` request. Mirrors the server
 * `CreatePostMetadata`; all fields are optional so the client only sends what it has
 * (this screen sends [carModelId] and [caption]).
 */
@Serializable
data class CreatePostMetadata(
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID? = null,
    val customBrand: String? = null,
    val customModel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val town: String? = null,
    val country: String? = null,
    val caption: String? = null,
    /** "CAMERA" or "GALLERY". Only camera posts earn SpotScore and count toward streaks. */
    val source: String? = null,
    /** IANA timezone ID (e.g. "Europe/Bucharest"). Used by the backend for local-day streak logic. */
    val createdAtTimezone: String? = null,
)
