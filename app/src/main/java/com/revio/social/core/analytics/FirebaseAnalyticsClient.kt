package com.revio.social.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.revio.social.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forwards [AnalyticsEvent]s to Firebase Analytics. Depends on the [FirebaseAnalytics] instance
 * already provided by [com.revio.social.core.feedback.FeedbackAnalyticsProviderModule] — that
 * provider is not duplicated here.
 *
 * Every event passes through [AnalyticsSanitizer] first, so forbidden keys and other violations
 * never reach Firebase — see `docs/telemetry-naming-and-forbidden-data.md`.
 */
@Singleton
class FirebaseAnalyticsClient @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsClient {

    override fun log(event: AnalyticsEvent) {
        val sanitized = AnalyticsSanitizer.sanitize(event, strict = BuildConfig.DEBUG)
        val bundle = Bundle().apply {
            sanitized.params.forEach { (key, value) ->
                when (value) {
                    is AnalyticsParamValue.StringValue -> putString(key, value.value)
                    is AnalyticsParamValue.LongValue -> putLong(key, value.value)
                    is AnalyticsParamValue.DoubleValue -> putDouble(key, value.value)
                }
            }
        }
        firebaseAnalytics.logEvent(sanitized.name, bundle)
    }
}
