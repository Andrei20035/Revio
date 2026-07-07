package com.revio.app.data.model

import java.util.UUID

data class AuthCredential(
    val id: UUID,
    val email: String,
    val password: String?,
    val provider: AuthProvider,
    val googleId: String?,
)