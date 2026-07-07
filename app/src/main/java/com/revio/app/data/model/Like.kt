package com.revio.app.data.model

import java.sql.Timestamp
import java.util.UUID

data class Like(
    val id: UUID,
    val userId: UUID,
    val postId: UUID,
    val createdAt: Timestamp? = null,
)