package com.revio.social.core.analytics

/**
 * A single analytics event: a stable name plus a small set of primitive parameters.
 *
 * This type is intentionally generic — unlike [com.revio.social.core.feedback.FeedbackEvent],
 * which is fixed to the feedback funnel's own fields, an [AnalyticsEvent] can represent any
 * domain (auth, onboarding, post creation, feed, ...).
 *
 * Naming, allowed parameters, and forbidden data are governed by
 * `docs/telemetry-naming-and-forbidden-data.md` (frozen separately) — this type does not
 * enforce those rules itself; validation is a later step.
 */
data class AnalyticsEvent(
    val name: String,
    val params: Map<String, AnalyticsParamValue> = emptyMap(),
)

/** The parameter value shapes Firebase Analytics accepts. */
sealed class AnalyticsParamValue {
    data class StringValue(val value: String) : AnalyticsParamValue()
    data class LongValue(val value: Long) : AnalyticsParamValue()
    data class DoubleValue(val value: Double) : AnalyticsParamValue()
}
