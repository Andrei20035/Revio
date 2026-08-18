package com.revio.social.core.earlyspotter

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.core.network.onValidatedReconnect
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.repository.AnnouncementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors the server's AnnouncementKey enum names — see UserAnnouncementTable.kt. */
const val EARLY_SPOTTER_WELCOME_KEY = "EARLY_SPOTTER_WELCOME"
const val EARLY_SPOTTER_BONUS_KEY = "EARLY_SPOTTER_BONUS"

/** One-time Early Spotter card currently eligible to show, or [Hidden] if none is. */
sealed interface EarlySpotterCardState {
    data object Hidden : EarlySpotterCardState
    data class Visible(val earlySpotterNumber: Int, val bonusPoints: Int) : EarlySpotterCardState
}

/**
 * Owns the one-time, combined Early Spotter welcome+bonus card: eligibility, the SEEN dedup, and
 * the offline ack retry queue. Server is canonical (via [AnnouncementRepository]); DataStore holds
 * two separate per-user sets — [UserPreferences.earlySpotterDismissed] (dedup: must not be shown
 * again) and [UserPreferences.pendingEarlySpotterAcks] (offline retry queue) — kept distinct so a
 * failing/offline ack can never resurrect the card, see either key's doc. Singleton for the same
 * reason [com.revio.social.core.tour.TourController] is: this card spans navigation destinations
 * and outlives any single screen's ViewModel scope.
 */
@Singleton
class EarlySpotterController @Inject constructor(
    private val announcementRepository: AnnouncementRepository,
    private val userPreferences: UserPreferences,
    networkConnectivityManager: NetworkConnectivityManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<EarlySpotterCardState>(EarlySpotterCardState.Hidden)
    val state: StateFlow<EarlySpotterCardState> = _state.asStateFlow()

    // Set by onProfileCreated()/refreshFromServer() so showCardIfEligible() (called once the
    // guided tour finishes) doesn't need a second round trip to know the number/bonus amount.
    private var pendingEarlySpotterNumber: Int? = null
    private var pendingBonusPoints: Int? = null

    init {
        scope.launch {
            userPreferences.userId.distinctUntilChanged().collect { userId ->
                if (userId != null) refreshFromServer(userId)
            }
        }
        scope.launch {
            // On reconnect, retry queued acks AND re-run the server refresh: a refresh that
            // failed earlier (e.g. offline at startup, or a transient server error) otherwise
            // never gets a second chance, leaving the card permanently unshown.
            networkConnectivityManager.onValidatedReconnect().collect {
                retryPendingAcks()
                userPreferences.userId.first()?.let { userId -> refreshFromServer(userId) }
            }
        }
    }

    /**
     * Called right after a successful profile creation with the server's response — the fast
     * path that avoids a round trip to the announcements endpoint. Only records the pending
     * number/bonus; the combined card itself is shown later by [showCardIfEligible], once the
     * guided tour finishes. No-ops if [isEarlySpotter] is false or [earlySpotterNumber] is null.
     */
    fun onProfileCreated(isEarlySpotter: Boolean, earlySpotterNumber: Int?, bonusPoints: Int?) {
        if (!isEarlySpotter || earlySpotterNumber == null) return
        pendingEarlySpotterNumber = earlySpotterNumber
        pendingBonusPoints = bonusPoints
    }

    /**
     * Recovery path for relogin/another device/app killed before the card was shown or
     * acknowledged — reconstructs pending state from the server's canonical PENDING list. A key
     * already in [UserPreferences.earlySpotterDismissed] was already dismissed locally and must
     * not be shown again, regardless of whether its server ack has succeeded yet — see that key's
     * doc for why this is deliberately not [UserPreferences.pendingEarlySpotterAcks]. A key
     * missing from the PENDING list entirely means the server already has it as SEEN, which
     * counts the same as dismissed.
     */
    private suspend fun refreshFromServer(userId: UUID) {
        val result = announcementRepository.getPending()
        val pending = (result as? ApiResult.Success)?.data ?: return
        val dismissedLocally = userPreferences.earlySpotterDismissed(userId).first()

        val welcome = pending.firstOrNull { it.key == EARLY_SPOTTER_WELCOME_KEY }
        val bonus = pending.firstOrNull { it.key == EARLY_SPOTTER_BONUS_KEY }

        pendingEarlySpotterNumber = welcome?.let { parseIntField(it.payload, "earlySpotterNumber") }
        pendingBonusPoints = bonus?.let { parseIntField(it.payload, "points") }

        val welcomeConsumed = welcome == null || welcome.key in dismissedLocally
        val bonusConsumed = bonus == null || bonus.key in dismissedLocally
        if (!(welcomeConsumed && bonusConsumed)) {
            showCardIfEligible()
        }
    }

    /** Shows the combined card if both the number and the bonus points are pending — a no-op otherwise. */
    fun showCardIfEligible() {
        val number = pendingEarlySpotterNumber ?: return
        val points = pendingBonusPoints ?: return
        _state.value = EarlySpotterCardState.Visible(number, points)
    }

    /** Dismisses the card and acknowledges both keys — idempotent, safe to call while offline. */
    fun onAcknowledged() {
        _state.value = EarlySpotterCardState.Hidden
        pendingEarlySpotterNumber = null
        pendingBonusPoints = null
        scope.launch {
            val userId = userPreferences.userId.first() ?: return@launch
            // Recorded immediately, independent of ack delivery — a failed or offline ack must
            // never resurrect the card just because the server hasn't confirmed SEEN yet.
            userPreferences.addEarlySpotterDismissed(userId, EARLY_SPOTTER_WELCOME_KEY)
            userPreferences.addEarlySpotterDismissed(userId, EARLY_SPOTTER_BONUS_KEY)
        }
        acknowledge(EARLY_SPOTTER_WELCOME_KEY)
        acknowledge(EARLY_SPOTTER_BONUS_KEY)
    }

    /**
     * Attempts the ack immediately; on failure (including being offline), queues [announcementKey]
     * in [UserPreferences.pendingEarlySpotterAcks] for [retryPendingAcks] to pick up later. The
     * card is already hidden locally by the caller either way — this only affects server sync.
     */
    private fun acknowledge(announcementKey: String) {
        scope.launch {
            val userId = userPreferences.userId.first() ?: return@launch
            val result = announcementRepository.acknowledge(announcementKey)
            if (result is ApiResult.Error) {
                userPreferences.addPendingEarlySpotterAck(userId, announcementKey)
            } else {
                userPreferences.removePendingEarlySpotterAck(userId, announcementKey)
            }
        }
    }

    private suspend fun retryPendingAcks() {
        val userId = userPreferences.userId.first() ?: return
        val pending = userPreferences.pendingEarlySpotterAcks(userId).first()
        pending.forEach { announcementKey ->
            val result = announcementRepository.acknowledge(announcementKey)
            if (result is ApiResult.Success) {
                userPreferences.removePendingEarlySpotterAck(userId, announcementKey)
            }
        }
    }

    private fun parseIntField(payload: String?, field: String): Int? {
        if (payload.isNullOrBlank()) return null
        return try {
            (Json.parseToJsonElement(payload) as? JsonObject)?.get(field)?.jsonPrimitive?.intOrNull
        } catch (e: Exception) {
            null
        }
    }
}
