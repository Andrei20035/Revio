package com.revio.social.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.revio.social.data.model.PromptStatus
import com.revio.social.data.remote.dto.device.RegisterDeviceRequest
import com.revio.social.data.remote.dto.feedback.SubmitFirstPostFeedbackRequest
import com.revio.social.data.remote.dto.feedback.SubmitUserFeedbackRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

/**
 * Tri-state guided-tour status. [Unknown] (key absent) is distinct from [Armed] so that
 * existing users can be grandfathered straight to [Completed] on first launch of a build
 * that introduces the tour, without ever seeing it.
 */
enum class TourStatus {
    Unknown,
    Armed,
    Completed,
}

/** Locally cached mirror of the server's first-post feedback prompt state — see [FeedbackPromptState][com.revio.social.data.model.FeedbackPromptState]. */
data class CachedPromptState(
    val status: PromptStatus,
    val shownCount: Int,
    val lastShownAt: Instant?,
)

internal fun CachedPromptState.serialize(): String =
    "${status.name}|$shownCount|${lastShownAt?.toEpochMilli() ?: ""}"

internal fun String.toCachedPromptState(): CachedPromptState? {
    val parts = split("|")
    if (parts.size != 3) return null
    val status = runCatching { PromptStatus.valueOf(parts[0]) }.getOrNull() ?: return null
    val shownCount = parts[1].toIntOrNull() ?: return null
    val lastShownAt = parts[2].takeIf { it.isNotEmpty() }
        ?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
    return CachedPromptState(status, shownCount, lastShownAt)
}

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {

    companion object {
        val ONBOARDING_KEY = booleanPreferencesKey("onboarding_completed")

        /** Opt-in consent (docs/consent-decision.md) — device-wide, not per-user, mirroring [ONBOARDING_KEY]. */
        val ANALYTICS_CONSENT_KEY = booleanPreferencesKey("analytics_consent_granted")

        /**
         * Whether the `POST_NOTIFICATIONS` runtime permission dialog has ever been shown —
         * device-wide, like [ONBOARDING_KEY]. Android's own APIs can't distinguish "never asked"
         * from "denied": `shouldShowRequestPermissionRationale` returns false in both cases
         * (before any request, and after a permanent denial), so the Notifications Settings
         * section (step 2.9) needs this local flag to tell them apart.
         */
        val NOTIFICATION_PERMISSION_REQUESTED_KEY = booleanPreferencesKey("notification_permission_requested")
        val JWT_TOKEN_KEY = stringPreferencesKey("jwt_token")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USERNAME_KEY = stringPreferencesKey("username")
        val EMAIL_KEY = stringPreferencesKey("email")

        /**
         * Device registration request that failed to reach the server (offline) and still needs
         * retrying — see [PushTokenRegistrar][com.revio.social.core.notifications.PushTokenRegistrar].
         * Device-wide, not per-user: only one account can be logged in on a device at a time
         * (single-active-session constraint), and a registration always targets the JWT's current
         * user, so there's never more than one pending upload to track.
         */
        val PENDING_DEVICE_REGISTRATION_KEY = stringPreferencesKey("pending_device_registration")

        /** Legacy device-wide key, superseded by [tourStatusKey] — read only for one-time migration. */
        private val LEGACY_TOUR_STATUS_KEY = stringPreferencesKey("guided_tour_status")

        private fun tourStatusKey(userId: UUID) =
            stringPreferencesKey("guided_tour_status_$userId")

        private fun firstPostFeedbackStateKey(userId: UUID) =
            stringPreferencesKey("first_post_feedback_state_$userId")

        private fun firstPostFeedbackPendingKey(userId: UUID) =
            stringPreferencesKey("first_post_feedback_pending_$userId")

        private fun firstPostFeedbackArmedKey(userId: UUID) =
            booleanPreferencesKey("first_post_feedback_armed_$userId")

        private fun userFeedbackPendingKey(userId: UUID) =
            stringPreferencesKey("user_feedback_pending_$userId")

        /**
         * Early Spotter announcement keys ("EARLY_SPOTTER_WELCOME"/"EARLY_SPOTTER_BONUS") whose
         * server ack failed (or hasn't been attempted while offline) and still needs retrying —
         * see [com.revio.social.core.earlyspotter.EarlySpotterController]. Deliberately NOT the
         * local dedup guard: whether a key must not be shown again is tracked separately by
         * [earlySpotterDismissedKey], so a stuck/failing ack (e.g. the server 500ing) can never
         * resurrect the card just because it's also absent from the retry queue by mistake.
         * Per-user like every other key here, not repeating the tour status' pre-A0a global-key
         * mistake.
         */
        private fun earlySpotterPendingAcksKey(userId: UUID) =
            stringSetPreferencesKey("early_spotter_pending_acks_$userId")

        /**
         * Early Spotter announcement keys the user has already dismissed locally, independent of
         * whether the server ack ever succeeded — the sole source of truth for "must not be shown
         * again" (see [earlySpotterPendingAcksKey] for why this is a separate key).
         */
        private fun earlySpotterDismissedKey(userId: UUID) =
            stringSetPreferencesKey("early_spotter_dismissed_$userId")

        /** Times the notifications pre-prompt fallback (D, step 2.11) has been shown to [userId] — capped at 3. */
        private fun notificationPrepromptShownCountKey(userId: UUID) =
            intPreferencesKey("notification_preprompt_shown_count_$userId")

        /** Epoch millis of the last time the fallback D prompt was shown to [userId] — enforces the ≥7-day reshow gap. */
        private fun notificationPrepromptLastShownAtKey(userId: UUID) =
            longPreferencesKey("notification_preprompt_last_shown_at_$userId")
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { it[ONBOARDING_KEY] ?: false }

    val notificationPermissionRequested: Flow<Boolean> = context.dataStore.data
        .map { it[NOTIFICATION_PERMISSION_REQUESTED_KEY] ?: false }

    /** On by default (opt-out) until the user explicitly revokes it — see docs/consent-decision.md. */
    val analyticsConsentGranted: Flow<Boolean> = context.dataStore.data
        .map { it[ANALYTICS_CONSENT_KEY] ?: true }

    /**
     * Same as [analyticsConsentGranted] but preserves `null` when the key is absent, so callers
     * can distinguish "no choice persisted yet" from an explicit `false` — see
     * docs/consent-decision.md.
     */
    val analyticsConsentGrantedOrNull: Flow<Boolean?> = context.dataStore.data
        .map { it[ANALYTICS_CONSENT_KEY] }

    /**
     * Per-user tour status, keyed by [userId] so a second account on a shared device never
     * inherits the first account's completed/armed state. On first read for [userId], if the
     * per-user key is absent but the pre-migration device-wide [LEGACY_TOUR_STATUS_KEY] holds a
     * value, that value is adopted as this user's status and the legacy key is deleted — so an
     * existing user who already completed the tour under the old scheme doesn't see it again.
     */
    suspend fun tourStatus(userId: UUID): TourStatus {
        var resolved = TourStatus.Unknown
        context.dataStore.edit { preferences ->
            val perUserKey = tourStatusKey(userId)
            val perUserValue = preferences[perUserKey]
            if (perUserValue != null) {
                resolved = runCatching { TourStatus.valueOf(perUserValue) }.getOrNull() ?: TourStatus.Unknown
            } else {
                val legacyValue = preferences[LEGACY_TOUR_STATUS_KEY]
                if (legacyValue != null) {
                    resolved = runCatching { TourStatus.valueOf(legacyValue) }.getOrNull() ?: TourStatus.Unknown
                    preferences[perUserKey] = resolved.name
                    preferences.remove(LEGACY_TOUR_STATUS_KEY)
                }
            }
        }
        return resolved
    }

    @Deprecated("Use TokenStore")
    val authToken: Flow<String?> = context.dataStore.data.map { it[JWT_TOKEN_KEY] }

    val userId: Flow<UUID?> = context.dataStore.data
        .map { preferences ->
            preferences[USER_ID_KEY]?.let { UUID.fromString(it) }
        }

    val username: Flow<String?> = context.dataStore.data
        .map { it[USERNAME_KEY] }

    val email: Flow<String?> = context.dataStore.data
        .map { it[EMAIL_KEY] }

    /** Device registration awaiting resubmission (queued while offline). `null` if none pending. */
    val pendingDeviceRegistration: Flow<RegisterDeviceRequest?> = context.dataStore.data
        .map { preferences ->
            preferences[PENDING_DEVICE_REGISTRATION_KEY]?.let {
                runCatching { json.decodeFromString(RegisterDeviceRequest.serializer(), it) }.getOrNull()
            }
        }

    /** Locally cached first-post feedback prompt state for [userId]. `null` if never cached. */
    fun firstPostFeedbackState(userId: UUID): Flow<CachedPromptState?> = context.dataStore.data
        .map { preferences -> preferences[firstPostFeedbackStateKey(userId)]?.toCachedPromptState() }

    /** Feedback response awaiting resubmission for [userId] (queued while offline). `null` if none pending. */
    fun pendingFirstPostFeedback(userId: UUID): Flow<SubmitFirstPostFeedbackRequest?> = context.dataStore.data
        .map { preferences ->
            preferences[firstPostFeedbackPendingKey(userId)]?.let {
                runCatching { json.decodeFromString(SubmitFirstPostFeedbackRequest.serializer(), it) }.getOrNull()
            }
        }

    /**
     * Whether [userId] has an armed-but-not-yet-shown first-post feedback prompt. Survives the
     * app being killed right after a successful first post, before the prompt had a chance to
     * appear on the next Feed/Profile visit.
     */
    fun firstPostFeedbackArmed(userId: UUID): Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[firstPostFeedbackArmedKey(userId)] ?: false }

    /** Settings feedback submission awaiting resubmission for [userId] (queued while offline). `null` if none pending. */
    fun pendingUserFeedback(userId: UUID): Flow<SubmitUserFeedbackRequest?> = context.dataStore.data
        .map { preferences ->
            preferences[userFeedbackPendingKey(userId)]?.let {
                runCatching { json.decodeFromString(SubmitUserFeedbackRequest.serializer(), it) }.getOrNull()
            }
        }

    /** Early Spotter announcement keys awaiting ack confirmation for [userId] — see [earlySpotterPendingAcksKey]. */
    fun pendingEarlySpotterAcks(userId: UUID): Flow<Set<String>> = context.dataStore.data
        .map { preferences -> preferences[earlySpotterPendingAcksKey(userId)] ?: emptySet() }

    suspend fun addPendingEarlySpotterAck(userId: UUID, announcementKey: String) {
        context.dataStore.edit { preferences ->
            val key = earlySpotterPendingAcksKey(userId)
            preferences[key] = (preferences[key] ?: emptySet()) + announcementKey
        }
    }

    suspend fun removePendingEarlySpotterAck(userId: UUID, announcementKey: String) {
        context.dataStore.edit { preferences ->
            val key = earlySpotterPendingAcksKey(userId)
            preferences[key] = (preferences[key] ?: emptySet()) - announcementKey
        }
    }

    /** Early Spotter announcement keys already dismissed locally for [userId] — see [earlySpotterDismissedKey]. */
    fun earlySpotterDismissed(userId: UUID): Flow<Set<String>> = context.dataStore.data
        .map { preferences -> preferences[earlySpotterDismissedKey(userId)] ?: emptySet() }

    /** How many times the fallback D notifications prompt (step 2.11) has been shown to [userId]. `0` if never. */
    fun notificationPrepromptShownCount(userId: UUID): Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[notificationPrepromptShownCountKey(userId)] ?: 0 }

    /** When the fallback D prompt was last shown to [userId]. `null` if never. */
    fun notificationPrepromptLastShownAt(userId: UUID): Flow<Instant?> = context.dataStore.data
        .map { preferences -> preferences[notificationPrepromptLastShownAtKey(userId)]?.let { Instant.ofEpochMilli(it) } }

    /** Records a fallback D reveal for [userId] — increments the shown count and stamps `now` as the last-shown time. */
    suspend fun recordNotificationPrepromptShown(userId: UUID) {
        context.dataStore.edit { preferences ->
            val countKey = notificationPrepromptShownCountKey(userId)
            preferences[countKey] = (preferences[countKey] ?: 0) + 1
            preferences[notificationPrepromptLastShownAtKey(userId)] = System.currentTimeMillis()
        }
    }

    suspend fun addEarlySpotterDismissed(userId: UUID, announcementKey: String) {
        context.dataStore.edit { preferences ->
            val key = earlySpotterDismissedKey(userId)
            preferences[key] = (preferences[key] ?: emptySet()) + announcementKey
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[ONBOARDING_KEY] = completed }
    }

    suspend fun setAnalyticsConsentGranted(granted: Boolean) {
        context.dataStore.edit { it[ANALYTICS_CONSENT_KEY] = granted }
    }

    suspend fun setNotificationPermissionRequested() {
        context.dataStore.edit { it[NOTIFICATION_PERMISSION_REQUESTED_KEY] = true }
    }

    suspend fun setTourStatus(userId: UUID, status: TourStatus) {
        context.dataStore.edit { it[tourStatusKey(userId)] = status.name }
    }

    suspend fun removeLegacyJwt() {
        context.dataStore.edit { it.remove(JWT_TOKEN_KEY) }
    }

    @Deprecated("Use TokenStore")
    suspend fun saveJwtToken(token: String) {
        context.dataStore.edit { it[JWT_TOKEN_KEY] = token }
    }
    suspend fun saveUserId(uuid: UUID) {
        context.dataStore.edit { it[USER_ID_KEY] = uuid.toString() }
    }

    suspend fun saveUsername(name: String) {
        context.dataStore.edit { it[USERNAME_KEY] = name }
    }

    suspend fun saveEmail(userEmail: String) {
        context.dataStore.edit { it[EMAIL_KEY] = userEmail }
    }

    suspend fun setFirstPostFeedbackState(userId: UUID, state: CachedPromptState) {
        context.dataStore.edit { it[firstPostFeedbackStateKey(userId)] = state.serialize() }
    }

    suspend fun setPendingFirstPostFeedback(userId: UUID, request: SubmitFirstPostFeedbackRequest?) {
        context.dataStore.edit { preferences ->
            val key = firstPostFeedbackPendingKey(userId)
            if (request == null) {
                preferences.remove(key)
            } else {
                preferences[key] = json.encodeToString(SubmitFirstPostFeedbackRequest.serializer(), request)
            }
        }
    }

    suspend fun setFirstPostFeedbackArmed(userId: UUID, armed: Boolean) {
        context.dataStore.edit { it[firstPostFeedbackArmedKey(userId)] = armed }
    }

    suspend fun setPendingDeviceRegistration(request: RegisterDeviceRequest?) {
        context.dataStore.edit { preferences ->
            if (request == null) {
                preferences.remove(PENDING_DEVICE_REGISTRATION_KEY)
            } else {
                preferences[PENDING_DEVICE_REGISTRATION_KEY] =
                    json.encodeToString(RegisterDeviceRequest.serializer(), request)
            }
        }
    }

    suspend fun setPendingUserFeedback(userId: UUID, request: SubmitUserFeedbackRequest?) {
        context.dataStore.edit { preferences ->
            val key = userFeedbackPendingKey(userId)
            if (request == null) {
                preferences.remove(key)
            } else {
                preferences[key] = json.encodeToString(SubmitUserFeedbackRequest.serializer(), request)
            }
        }
    }

    /**
     * Clears device-session identity only. Deliberately leaves every per-user key (tour status,
     * first-post feedback, user feedback, Early Spotter pending acks) untouched — they're already
     * scoped by userId, so they neither need clearing on logout nor leak into whichever account
     * logs in next.
     */
    suspend fun clearAuthData() {
        context.dataStore.edit {
            it.remove(JWT_TOKEN_KEY)
            it.remove(USER_ID_KEY)
            it.remove(USERNAME_KEY)
            it.remove(EMAIL_KEY)
        }
    }

    /**
     * Resets the onboarding status to false.
     * This is useful for testing the onboarding flow without having to uninstall the app.
     */
    suspend fun resetOnboardingStatus() {
        setOnboardingCompleted(false)
    }
}
