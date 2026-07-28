package com.revio.social.features.profile.dashboard

import java.util.UUID

enum class TileImageState { Loading, Success, Error, Retrying }

data class PostImageKey(val postId: UUID, val imageUrl: String)

const val IMAGE_LOAD_TIMEOUT_MS = 8_000L
