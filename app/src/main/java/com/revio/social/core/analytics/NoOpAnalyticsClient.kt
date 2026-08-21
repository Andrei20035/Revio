package com.revio.social.core.analytics

import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op [AnalyticsClient] for builds/tests where events must not reach real Analytics.
 * Not yet bound anywhere — the debug/release split lands in a later step.
 */
@Singleton
class NoOpAnalyticsClient @Inject constructor() : AnalyticsClient {
    override fun log(event: AnalyticsEvent) = Unit
}
