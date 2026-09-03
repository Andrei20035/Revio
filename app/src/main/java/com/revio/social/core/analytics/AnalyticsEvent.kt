package com.revio.social.core.analytics

/**
 * Ev. push_destination_reached (§16, pas 7.2) — a deep link's destination screen was actually
 * reached (navigated to), as opposed to merely tapped (that's `push_opened`, logged from
 * [com.revio.social.core.notifications.PendingDeepLink.capture]). Shared here — rather than a
 * private per-file constant — because more than one call site fires it
 * ([com.revio.social.features.profile.dashboard.ProfileDashboardViewModel] for LIKE/COMMENT,
 * [com.revio.social.core.notifications.PendingDeepLink.logDestinationReached] for CHALLENGE), and
 * the name must not drift between them.
 */
const val EVENT_PUSH_DESTINATION_REACHED = "push_destination_reached"

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
