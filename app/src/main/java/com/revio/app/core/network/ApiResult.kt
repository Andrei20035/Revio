package com.revio.app.core.network

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
