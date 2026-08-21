package com.revio.social.core.analytics

/** Thrown by [AnalyticsSanitizer.sanitize] in strict mode when [AnalyticsEvent] violates a rule. */
class AnalyticsValidationException(message: String) : IllegalArgumentException(message)

private val NAME_PATTERN = Regex("^[a-z][a-z0-9]*(_[a-z0-9]+)*$")
private const val MAX_NAME_LENGTH = 40
private const val MAX_STRING_VALUE_LENGTH = 100

/**
 * Parameter keys that must never appear on an analytics event, regardless of their value —
 * see `docs/telemetry-naming-and-forbidden-data.md` for the full rationale. This list matches
 * that document's forbidden-data categories and forbidden identifiers.
 */
private val FORBIDDEN_PARAM_KEYS = setOf(
    "email", "phone", "phone_number", "username", "full_name", "name",
    "birth_date", "date_of_birth", "password",
    "token", "access_token", "refresh_token",
    "caption", "comment", "comment_text",
    "request_body", "response_body",
    "latitude", "longitude",
    "image_url", "image_path", "object_key",
    "user_id", "post_id", "credential_id", "device_id",
)

/** The fixed bucket vocabularies frozen in `docs/telemetry-naming-and-forbidden-data.md`. */
private val BUCKET_KEY_ALLOWED_VALUES: Map<String, Set<String>> = mapOf(
    "duration_bucket" to setOf("lt_1s", "1_3s", "3_10s", "10_30s", "gte_30s"),
    "size_bucket" to setOf("lt_500kb", "500kb_1mb", "1_2mb", "gte_2mb"),
    "retry_bucket" to setOf("0", "1", "2", "gte_3"),
)

/**
 * Enforces the naming, length, forbidden-key, and bucket rules frozen in
 * `docs/telemetry-naming-and-forbidden-data.md`. Not wired to any sender yet — a later step
 * decides which caller invokes this and with what [strict] value.
 */
object AnalyticsSanitizer {

    /**
     * @param strict When `true` (intended for debug builds), any violation throws
     * [AnalyticsValidationException] so it's caught during development. When `false` (intended
     * for release builds), violations are corrected in place — an oversized value is truncated,
     * a disallowed parameter is dropped — so a bug here never crashes a real user's session.
     */
    fun sanitize(event: AnalyticsEvent, strict: Boolean): AnalyticsEvent {
        val name = sanitizeName(event.name, strict)
        val params = buildMap<String, AnalyticsParamValue> {
            event.params.forEach { (key, value) ->
                sanitizeParam(key, value, strict)?.let { put(key, it) }
            }
        }
        return AnalyticsEvent(name = name, params = params)
    }

    private fun sanitizeName(name: String, strict: Boolean): String {
        if (name.length <= MAX_NAME_LENGTH && NAME_PATTERN.matches(name)) return name
        if (strict) {
            throw AnalyticsValidationException(
                "Event name \"$name\" must be snake_case and at most $MAX_NAME_LENGTH characters",
            )
        }
        return name.take(MAX_NAME_LENGTH)
    }

    /** Returns the (possibly corrected) value, or `null` if the parameter must be dropped. */
    private fun sanitizeParam(key: String, value: AnalyticsParamValue, strict: Boolean): AnalyticsParamValue? {
        if (key.length > MAX_NAME_LENGTH || !NAME_PATTERN.matches(key) || key in FORBIDDEN_PARAM_KEYS) {
            if (strict) throw AnalyticsValidationException("Parameter key \"$key\" is not allowed")
            return null
        }

        val allowedBucketValues = BUCKET_KEY_ALLOWED_VALUES[key]
        if (allowedBucketValues != null) {
            val stringValue = (value as? AnalyticsParamValue.StringValue)?.value
            if (stringValue !in allowedBucketValues) {
                if (strict) {
                    throw AnalyticsValidationException(
                        "\"$key\" must be one of $allowedBucketValues, was \"$stringValue\"",
                    )
                }
                return null
            }
        }

        return when {
            value is AnalyticsParamValue.StringValue && value.value.length > MAX_STRING_VALUE_LENGTH -> {
                if (strict) {
                    throw AnalyticsValidationException(
                        "Value for \"$key\" exceeds $MAX_STRING_VALUE_LENGTH characters",
                    )
                }
                AnalyticsParamValue.StringValue(value.value.take(MAX_STRING_VALUE_LENGTH))
            }
            else -> value
        }
    }
}
