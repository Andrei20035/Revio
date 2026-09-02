package com.revio.social.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.notifications.NotificationPrepromptController
import com.revio.social.core.tour.TourController
import com.revio.social.data.local.cache.FeedCache
import com.revio.social.data.local.preferences.TourStatus
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.local.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ev. 1 — fired once per app start with the destination this ViewModel resolved to. */
private const val EVENT_APP_START = "app_start"

/** ev. 2 — fired only when an existing session is actually evaluated (onboarding already done). */
private const val EVENT_SESSION_RESTORE_RESULT = "session_restore_result"

@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val tokenStore: TokenStore? = null,
    private val tourController: TourController? = null,
    private val feedCache: FeedCache? = null,
    private val analyticsClient: AnalyticsClient? = null,
    private val notificationPrepromptController: NotificationPrepromptController? = null,
) : ViewModel() {
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val onboardingDone = userPreferences.onboardingCompleted.first()
            val tokens = tokenStore?.read()
            val legacyToken = if (tokenStore == null) userPreferences.authToken.first() else null
            if (tokenStore != null) userPreferences.removeLegacyJwt()
            val userId = userPreferences.userId.first()

            val destination = when {
                !onboardingDone -> Screen.Onboarding.route
                tokens == null && legacyToken.isNullOrBlank() -> {
                    logSessionRestoreResult(outcome = "failure", failureCode = "no_token")
                    Screen.Auth.route
                }
                userId == null -> {
                    tokenStore?.clear()
                    userPreferences.clearAuthData()
                    feedCache?.clear()
                    logSessionRestoreResult(outcome = "failure", failureCode = "missing_user_id")
                    Screen.Auth.route
                }
                else -> {
                    // Valid session on this device. Grandfather pre-existing users (key
                    // absent) straight to Completed so the tour never surprises them, then
                    // let the controller resume an already-armed tour from Feed.
                    if (tourController != null && userId != null) {
                        if (userPreferences.tourStatus(userId) == TourStatus.Unknown) {
                            userPreferences.setTourStatus(userId, TourStatus.Completed)
                        }
                        tourController.startIfArmed()
                    }
                    // Fire-and-forget (step 1.2) — the only trigger point for a user who stays
                    // logged in across an app update; see NotificationPrepromptController
                    // .onSessionRestored. Never allowed to affect destination resolution: the
                    // call itself is non-suspend (can't delay _startDestination), and runCatching
                    // guards against a misbehaving controller throwing synchronously.
                    runCatching { notificationPrepromptController?.onSessionRestored() }
                    logSessionRestoreResult(outcome = "success")
                    Screen.Feed.route
                }
            }
            logAppStart(destination)
            _startDestination.value = destination
        }
    }

    private fun logAppStart(destination: String) {
        val destinationValue = when (destination) {
            Screen.Onboarding.route -> "onboarding"
            Screen.Auth.route -> "auth"
            Screen.Feed.route -> "feed"
            else -> destination
        }
        analyticsClient?.log(
            AnalyticsEvent(
                name = EVENT_APP_START,
                params = mapOf("destination" to AnalyticsParamValue.StringValue(destinationValue)),
            )
        )
    }

    /** [failureCode] is a fixed set for this event only: `no_token`, `missing_user_id`. */
    private fun logSessionRestoreResult(outcome: String, failureCode: String? = null) {
        val params = buildMap<String, AnalyticsParamValue> {
            put("outcome", AnalyticsParamValue.StringValue(outcome))
            failureCode?.let { put("failure_code", AnalyticsParamValue.StringValue(it)) }
        }
        analyticsClient?.log(AnalyticsEvent(name = EVENT_SESSION_RESTORE_RESULT, params = params))
    }
}
