package com.revio.app.core.network

import kotlinx.serialization.Serializable

@Serializable
sealed class ApiResult<out T> {
    @Serializable
    data class Success<out T>(val data: T) : ApiResult<T>()
    @Serializable
    data class Error(val message: String, val code: String? = null) : ApiResult<Nothing>()
}
