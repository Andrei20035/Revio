package com.revio.app.data.remote.dto.user

import com.revio.app.core.network.serialization.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class UpdateUserRequest(
    val fullName: String? = null,
    val username: String? = null,
    val country: String? = null,
    @Serializable(with = LocalDateSerializer::class)
    val birthDate: LocalDate? = null,
    val phoneNumber: String? = null,
)
