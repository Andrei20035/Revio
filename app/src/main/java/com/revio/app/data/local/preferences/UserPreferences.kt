package com.revio.app.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val ONBOARDING_KEY = booleanPreferencesKey("onboarding_completed")
        val JWT_TOKEN_KEY = stringPreferencesKey("jwt_token")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USERNAME_KEY = stringPreferencesKey("username")
        val EMAIL_KEY = stringPreferencesKey("email")
        val TOUR_STATUS_KEY = stringPreferencesKey("guided_tour_status")
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
