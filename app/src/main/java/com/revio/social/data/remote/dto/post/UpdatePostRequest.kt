package com.revio.social.data.remote.dto.post

import com.revio.social.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Body of `PATCH /posts/{postId}`. Mirrors the server `UpdatePostRequest`, which treats this as a
 * full replacement, not a partial patch: every request must resend the car identification —
 * either [carModelId], or [customBrand] + [customModel] (mutually exclusive) — even when only the
 * caption is changing, and [caption] is set to the post's caption on every request (`null` clears
 * it, it does not mean "leave unchanged").
 */
@Serializable
data class UpdatePostRequest(
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID? = null,
    val customBrand: String? = null,
    val customModel: String? = null,
    val caption: String? = null,
)
