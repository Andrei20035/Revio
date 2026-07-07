package com.revio.app.data.model


import com.revio.app.core.network.serialization.InstantSerializer
import com.revio.app.core.network.serialization.LocalDateSerializer
import com.revio.app.core.network.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID


@Serializable
data class User(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val profilePicturePath: String? = null,
    val fullName: String,
    val phoneNumber: String? = null,
    @Serializable(with = LocalDateSerializer::class)
    val birthDate: LocalDate? = null,
    val username: String,
    val country: String,
    val spotScore: Int = 0,
    val postCount: Int = 0,
    val isEarlySpotter: Boolean = false,
    val earlySpotterNumber: Int? = null,
    val streakDays: Int = 0,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant? = null,
) {
    val showEarlySpotter: Boolean get() = isEarlySpotter && earlySpotterNumber != null
}
