package com.revio.social.core.network

/**
 * Per-call-site reporting policy for [safeApiCall]/[safeApiCallNoContent]. Declaring this policy
 * does not, by itself, change any reporting behavior — a later step reads it to decide whether
 * an unexpected exception becomes a Crashlytics non-fatal.
 */
enum class ErrorPolicy {
    /** Default — an unexpected exception here is a bug worth a future non-fatal report. */
    REPORT,

    /** Best-effort by design — failure is expected and must never generate a report. */
    SILENT,

    /** A business-rule rejection (e.g. EMAIL_TAKEN) — surfaced to the user, never a crash report. */
    BUSINESS,
}
