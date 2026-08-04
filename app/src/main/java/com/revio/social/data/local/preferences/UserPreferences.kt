package com.revio.social.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.revio.social.data.model.PromptStatus
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
        val JWT_TOKEN_KEY = stringPreferencesKey("jwt_token")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USERNAME_KEY = stringPreferencesKey("username")
        val EMAIL_KEY = stringPreferencesKey("email")
        val TOUR_STATUS_KEY = stringPreferencesKey("guided_tour_status")

        private fun firstPostFeedbackStateKey(userId: UUID) =
            stringPreferencesKey("first_post_feedback_state_$userId")

        private fun firstPostFeedbackPendingKey(userId: UUID) =
            stringPreferencesKey("first_post_feedback_pending_$userId")

        private fun firstPostFeedbackArmedKey(userId: UUID) =
            booleanPreferencesKey("first_post_feedback_armed_$userId")

        private fun userFeedbackPendingKey(userId: UUID) =
            stringPreferencesKey("user_feedback_pending_$userId")
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { it[ONBOARDING_KEY] ?: false }

    val tourStatus: Flow<TourStatus> = context.dataStore.data
        .map { preferences ->
            preferences[TOUR_STATUS_KEY]?.let { runCatching { TourStatus.valueOf(it) }.getOrNull() }
                ?: TourStatus.Unknown
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

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[ONBOARDING_KEY] = completed }
    }

    suspend fun setTourStatus(status: TourStatus) {
        context.dataStore.edit { it[TOUR_STATUS_KEY] = status.name }
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
