package com.revio.social.core.analytics

/**
 * General-purpose analytics logging, decoupled from any single feature.
 *
 * No implementation or Hilt binding exists yet — that lands in a later step, alongside the
 * consent gate and sanitization. This interface has no consumers today.
 */
interface AnalyticsClient {
    fun log(event: AnalyticsEvent)
}
