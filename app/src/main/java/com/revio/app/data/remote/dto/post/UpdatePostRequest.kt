package com.revio.app.data.remote.dto.post

import com.revio.app.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Body of `PATCH /posts/{postId}`. Mirrors the server `UpdatePostRequest`: a null field
 * means "leave unchanged", except [carModelId] vs [customBrand]/[customModel] which are
 * mutually exclusive (whichever side is set replaces the post's car).
 */
@Serializable
data class UpdatePostRequest(
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID? = null,
    val customBrand: String? = null,
    val customModel: String? = null,
    val caption: String? = null,
)
