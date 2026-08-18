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
    data class Welcome(val earlySpotterNumber: Int) : EarlySpotterCardState
    data class Bonus(val points: Int) : EarlySpotterCardState
}

/**
 * Owns the one-time Early Spotter welcome/bonus cards: eligibility, the SEEN dedup, and the
 * offline ack retry queue. Server is canonical (via [AnnouncementRepository]); DataStore
 * ([UserPreferences.pendingEarlySpotterAcks]) is a per-user cache that also serves as the offline
 * retry queue — see that key's doc for why one set covers both jobs. Singleton for the same
 * reason [com.revio.social.core.tour.TourController] is: these cards span navigation destinations
 * and outlive any single screen's ViewModel scope.
 *
 * Deliberately does not talk to [com.revio.social.core.overlay.AppOverlayCoordinator] yet, and
 * exposes no UI — wiring this controller's state into the coordinator, the actual card
 * composables, and the call site that invokes [onProfileCreated] are later steps built on top of
 * this controller.
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

    // Set by onProfileCreated()/refreshFromServer() so showBonusIfEligible() (called once the
    // guided tour finishes, per the sequencing built on top of this controller) doesn't need a
    // second round trip to know the bonus amount.
    private var pendingBonusPoints: Int? = null

    init {
        scope.launch {
            userPreferences.userId.distinctUntilChanged().collect { userId ->
                if (userId != null) refreshFromServer(userId)
            }
        }
        scope.launch {
            networkConnectivityManager.onValidatedReconnect().collect { retryPendingAcks() }
        }
    }

    /**
     * Called right after a successful profile creation with the server's response — the fast
     * path that avoids a round trip to the announcements endpoint. No-ops if [isEarlySpotter] is
     * false or [earlySpotterNumber] is null.
     */
    fun onProfileCreated(isEarlySpotter: Boolean, earlySpotterNumber: Int?, bonusPoints: Int?) {
        if (!isEarlySpotter || earlySpotterNumber == null) return
        pendingBonusPoints = bonusPoints
        _state.value = EarlySpotterCardState.Welcome(earlySpotterNumber)
    }

    /**
     * Recovery path for relogin/another device/app killed before the welcome card was shown or
     * acknowledged — reconstructs pending state from the server's canonical PENDING list. A key
     * already in [UserPreferences.pendingEarlySpotterAcks] was already dismissed locally (its ack
     * just hasn't been confirmed yet) and must not be shown again.
     */
    private suspend fun refreshFromServer(userId: UUID) {
        val result = announcementRepository.getPending()
        val pending = (result as? ApiResult.Success)?.data ?: return
        val locallyDismissed = userPreferences.pendingEarlySpotterAcks(userId).first()

        val bonus = pending.firstOrNull { it.key == EARLY_SPOTTER_BONUS_KEY }
        pendingBonusPoints = bonus?.let { parseIntField(it.payload, "points") }

        val welcome = pending.firstOrNull { it.key == EARLY_SPOTTER_WELCOME_KEY }
        when {
            welcome != null && welcome.key !in locallyDismissed -> {
                val number = parseIntField(welcome.payload, "earlySpotterNumber")
                if (number != null) _state.value = EarlySpotterCardState.Welcome(number)
            }
            bonus != null && bonus.key !in locallyDismissed && pendingBonusPoints != null -> {
                _state.value = EarlySpotterCardState.Bonus(pendingBonusPoints!!)
            }
        }
    }

    /** Dismisses the welcome card and acknowledges it — idempotent, safe to call while offline. */
    fun onWelcomeAcknowledged() {
        _state.value = EarlySpotterCardState.Hidden
        acknowledge(EARLY_SPOTTER_WELCOME_KEY)
    }

    /** Shows the bonus card if one is pending and not yet acknowledged — a no-op otherwise. */
    fun showBonusIfEligible() {
        val points = pendingBonusPoints ?: return
        _state.value = EarlySpotterCardState.Bonus(points)
    }

    /** Dismisses the bonus card and acknowledges it — idempotent, safe to call while offline. */
    fun onBonusAcknowledged() {
        _state.value = EarlySpotterCardState.Hidden
        pendingBonusPoints = null
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
