package com.revio.social.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

const val ERROR_CODE_NETWORK = "network_unavailable"
const val NETWORK_ERROR_MESSAGE = "Network error"

@Serializable
sealed class ApiResult<out T> {
    @Serializable
    data class Success<out T>(val data: T) : ApiResult<T>()
    @Serializable
    data class Error(val message: String, val code: String? = null) : ApiResult<Nothing>() {
        /**
         * The original exception, if any — for crash reporting only, never shown to the user.
         * Not a primary-constructor property, so it's excluded from equals/hashCode/toString/
         * copy (two [Error]s with the same [message]/[code] are still equal regardless of
         * [cause]), and [Transient] excludes it from serialization.
         */
        @Transient
        var cause: Throwable? = null
            internal set

        /** Correlates this error with the matching server-side log line — see pas 3.1. Same exclusions as [cause]. */
        @Transient
        var requestId: String? = null
            internal set

        /** Set by [safeApiCall]/[safeApiCallNoContent] from their `policy` parameter. Same exclusions as [cause]. */
        @Transient
        var policy: ErrorPolicy = ErrorPolicy.REPORT
            internal set
    }
}

val ApiResult.Error.isNetworkError: Boolean
    get() = code == ERROR_CODE_NETWORK

/** Transforms a successful result's data while passing an [ApiResult.Error] through unchanged, [code] included. */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Error -> this
}
