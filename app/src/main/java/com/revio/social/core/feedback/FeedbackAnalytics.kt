package com.revio.social.core.feedback

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Funnel event names for the first-post feedback prompt, all sharing the `fp_feedback_` prefix. */
enum class FeedbackEventName(val eventName: String) {
    ELIGIBLE("fp_feedback_eligible"),
    SCHEDULED("fp_feedback_scheduled"),
    SHOWN("fp_feedback_shown"),
    CLOSED_X("fp_feedback_closed_x"),
    NOT_NOW("fp_feedback_not_now"),
    RATING_SELECTED("fp_feedback_rating_selected"),
    REASON_SELECTED("fp_feedback_reason_selected"),
    SECONDARY_OPENED("fp_feedback_secondary_opened"),
    COMMENT_TYPED("fp_feedback_comment_typed"),
    SUBMITTED("fp_feedback_submitted"),
    COMMENT_SKIPPED("fp_feedback_comment_skipped"),
    ABANDONED_NAVIGATION("fp_feedback_abandoned_navigation"),
    RESHOWN_AFTER_COOLDOWN("fp_feedback_reshown_after_cooldown"),
    SUBMITTED_SECOND_SHOW("fp_feedback_submitted_second_show"),

    // Settings feedback funnel — sharing the `sf_` prefix, distinct from the first-post prompt.
    ROW_VIEWED("sf_row_viewed"),
    SCREEN_OPENED("sf_screen_opened"),
    CATEGORY_SELECTED("sf_category_selected"),
    CATEGORY_CHANGED("sf_category_changed"),
    MESSAGE_STARTED("sf_message_started"),
    SEND_PRESSED("sf_send_pressed"),
    SENT("sf_sent"),
    SEND_FAILED("sf_send_failed"),
    RETRY_PRESSED("sf_retry_pressed"),
    ABANDONED("sf_abandoned"),
    ANOTHER_STARTED("sf_another_started"),
}

/**
 * A single funnel event. Only the parameters explicitly allowed by the feedback spec are
 * carried — never tokens, passwords, exact location, photos, other-form content, full logs,
 * or sensitive technical messages.
 */
data class FeedbackEvent(
    val name: FeedbackEventName,
    val rating: Int? = null,
    val reason: String? = null,
    val surface: String? = null,
    val hasComment: Boolean? = null,
    val showIndex: Int? = null,
    /** Settings feedback funnel only — the selected `FeedbackCategory` name, never free text. */
    val category: String? = null,
    /** Settings feedback funnel only — the selected `FeedbackArea` name, never free text. */
    val area: String? = null,
    /** Settings feedback funnel only — the `FeedbackSource` name, never free text. */
    val source: String? = null,
)

interface Analytics {
    fun log(event: FeedbackEvent)
}

/**
 * Adapts [FeedbackEvent] onto [AnalyticsClient] — the feedback funnel keeps its own event
 * catalogue and parameter shape (unchanged), but delegates the actual logging, and whatever
 * build-type/consent gating [AnalyticsClient] applies, to the shared abstraction.
 */
@Singleton
class FeedbackAnalyticsAdapter @Inject constructor(
    private val analyticsClient: AnalyticsClient,
) : Analytics {

    override fun log(event: FeedbackEvent) {
        val params = buildMap<String, AnalyticsParamValue> {
            event.rating?.let { put("rating", AnalyticsParamValue.LongValue(it.toLong())) }
            event.reason?.let { put("reason", AnalyticsParamValue.StringValue(it)) }
            event.surface?.let { put("surface", AnalyticsParamValue.StringValue(it)) }
            // AnalyticsParamValue has no boolean variant — encoded as 0/1, same as Firebase's
            // own numeric param types.
            event.hasComment?.let { put("has_comment", AnalyticsParamValue.LongValue(if (it) 1L else 0L)) }
            event.showIndex?.let { put("show_index", AnalyticsParamValue.LongValue(it.toLong())) }
            event.category?.let { put("category", AnalyticsParamValue.StringValue(it)) }
            event.area?.let { put("area", AnalyticsParamValue.StringValue(it)) }
            event.source?.let { put("source", AnalyticsParamValue.StringValue(it)) }
        }
        analyticsClient.log(AnalyticsEvent(name = event.name.eventName, params = params))
    }
}

@Module
@InstallIn(SingletonComponent::class)
object FeedbackAnalyticsProviderModule {
    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics {
        return FirebaseAnalytics.getInstance(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FeedbackAnalyticsModule {
    @Binds
    @Singleton
    abstract fun bindAnalytics(impl: FeedbackAnalyticsAdapter): Analytics
}
