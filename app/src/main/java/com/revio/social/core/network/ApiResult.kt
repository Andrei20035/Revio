package com.revio.social.core.network

import kotlinx.serialization.Serializable

const val ERROR_CODE_NETWORK = "network_unavailable"
const val NETWORK_ERROR_MESSAGE = "Network error"

@Serializable
sealed class ApiResult<out T> {
    @Serializable
    data class Success<out T>(val data: T) : ApiResult<T>()
    @Serializable
    data class Error(val message: String, val code: String? = null) : ApiResult<Nothing>()
}

val ApiResult.Error.isNetworkError: Boolean
    get() = code == ERROR_CODE_NETWORK

/** Transforms a successful result's data while passing an [ApiResult.Error] through unchanged, [code] included. */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Error -> this
}
