package com.revio.social.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forwards [AnalyticsEvent]s to Firebase Analytics. Depends on the [FirebaseAnalytics] instance
 * already provided by [com.revio.social.core.feedback.FeedbackAnalyticsProviderModule] — that
 * provider is not duplicated here.
 */
@Singleton
class FirebaseAnalyticsClient @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsClient {

    override fun log(event: AnalyticsEvent) {
        val bundle = Bundle().apply {
            event.params.forEach { (key, value) ->
                when (value) {
                    is AnalyticsParamValue.StringValue -> putString(key, value.value)
                    is AnalyticsParamValue.LongValue -> putLong(key, value.value)
                    is AnalyticsParamValue.DoubleValue -> putDouble(key, value.value)
                }
            }
        }
        firebaseAnalytics.logEvent(event.name, bundle)
    }
}
