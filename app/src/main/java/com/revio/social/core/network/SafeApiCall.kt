package com.revio.social.core.network

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import java.io.IOException

/** HTTP status codes at or above this are server bugs (taxonomy category 1), never business rejections. */
private const val SERVER_ERROR_STATUS_THRESHOLD = 500

suspend fun safeApiCallNoContent(
    policy: ErrorPolicy = ErrorPolicy.REPORT,
    apiCall: suspend () -> Response<Unit>,
): ApiResult<Unit> {
    val result = try {
        val response = apiCall()
        if (response.isSuccessful) {
            ApiResult.Success(Unit)
        } else {
            val errorBody = response.errorBody()?.string()
            val error = extractApiError(errorBody)
            if (response.code() >= SERVER_ERROR_STATUS_THRESHOLD) {
                reportNonFatal(policy, ServerErrorException(response.code(), error.first))
            }
            ApiResult.Error(error.first, error.second)
        }
    } catch (e: IOException) {
        ApiResult.Error(NETWORK_ERROR_MESSAGE, code = ERROR_CODE_NETWORK)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reportNonFatal(policy, e)
        ApiResult.Error("Unexpected error: ${e.message}")
    }
    if (result is ApiResult.Error) result.policy = policy
    return result
}

suspend fun <T> safeApiCall(
    policy: ErrorPolicy = ErrorPolicy.REPORT,
    apiCall: suspend () -> Response<T>,
): ApiResult<T> {
    val result = try {
        val response = apiCall()

        if (response.isSuccessful) {
            response.body()?.let {
                ApiResult.Success(it)
            } ?: ApiResult.Error("Empty response")
        } else {

            val errorBody = response.errorBody()?.string()
            val error = extractApiError(errorBody)
            if (response.code() >= SERVER_ERROR_STATUS_THRESHOLD) {
                reportNonFatal(policy, ServerErrorException(response.code(), error.first))
            }
            ApiResult.Error(error.first, error.second)
        }

    } catch (e: IOException) {
        ApiResult.Error(NETWORK_ERROR_MESSAGE, code = ERROR_CODE_NETWORK)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reportNonFatal(policy, e)
        ApiResult.Error("Unexpected error: ${e.message}")
    }
    if (result is ApiResult.Error) result.policy = policy
    return result
}

/**
 * Taxonomy category 1 ("Neașteptat") only: 5xx responses and exceptions from the generic
 * catch block (e.g. `JsonDecodingException`). Never called for [IOException] (offline —
 * category 4) or [CancellationException] (control flow — category 4, and rethrown before
 * reaching here). [ErrorPolicy.SILENT]/[ErrorPolicy.BUSINESS] call sites opt out entirely.
 */
private fun reportNonFatal(policy: ErrorPolicy, throwable: Throwable) {
    if (policy != ErrorPolicy.REPORT) return
    try {
        FirebaseCrashlytics.getInstance().recordException(throwable)
    } catch (_: Exception) {
        // Reporting must never break the real call path (e.g. Firebase not initialized in tests).
    }
}

/** Synthetic exception so a 5xx response (no [Throwable] of its own) still reports with a stack trace. */
private class ServerErrorException(statusCode: Int, message: String) :
    Exception("Unexpected server error: HTTP $statusCode ($message)")

private fun extractApiError(errorBody: String?): Pair<String, String?> {
    if (errorBody.isNullOrBlank()) return "Unknown error" to null
    val trimmedBody = errorBody.trimStart()
    val looksLikeJsonObject = trimmedBody.startsWith("{")
    val fallbackMessage = if (errorBody.length > 200 || trimmedBody.startsWith("<")) {
        "Server error"
    } else {
        errorBody
    }

    if (!looksLikeJsonObject) return fallbackMessage to null

    return try {
        val json = Json.parseToJsonElement(errorBody) as? JsonObject
        val nestedError = json?.get("error") as? JsonObject
        val message = nestedError?.get("message")?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: json?.get("error")?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: json?.get("message")?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: "Unknown error"
        // Auth errors nest `code` under `error` ({"error": {"code": ..., "message": ...}}); other
        // routes (e.g. PATCH /posts/{postId}'s 409) send it as a flat sibling of `error`
        // ({"error": "...", "code": "..."}) — fall back to the flat shape when there's no nested one.
        val code = nestedError?.get("code")?.jsonPrimitive?.contentOrNull
            ?: json?.get("code")?.jsonPrimitive?.contentOrNull
        message to code
    } catch (_: Exception) {
        fallbackMessage to null
    }
}
