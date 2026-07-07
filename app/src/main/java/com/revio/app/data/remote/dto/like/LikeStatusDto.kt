package com.revio.app.data.remote.dto.like

import com.revio.app.data.model.LikeStatus
import kotlinx.serialization.Serializable

/**
 * Network shape of the server `LikeStatusDTO` returned by `GET`/`POST posts/{postId}/likes`.
 */
@Serializable
data class LikeStatusDto(
    val liked: Boolean,
    val count: Long,
)

fun LikeStatusDto.toDomain(): LikeStatus = LikeStatus(liked = liked, count = count)
