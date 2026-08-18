package com.revio.social.core.earlyspotter

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.remote.dto.announcement.AnnouncementDTO
import com.revio.social.data.repository.AnnouncementRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class EarlySpotterControllerTest {

    private val userId: UUID = UUID.randomUUID()

    private fun defaultAnnouncementRepository(): AnnouncementRepository =
        mockk<AnnouncementRepository>().apply {
            coEvery { getPending() } returns ApiResult.Success(emptyList())
        }

    private fun defaultUserPreferences(): UserPreferences =
        mockk<UserPreferences>(relaxed = true).apply {
            every { this@apply.userId } returns flowOf(this@EarlySpotterControllerTest.userId)
            // refreshFromServer() awaits this unconditionally — a relaxed mock's default Flow
            // never emits, which would hang the collector in init{} forever on a real thread.
            every { earlySpotterDismissed(any()) } returns flowOf(emptySet())
        }

    private fun defaultNetworkConnectivityManager(): NetworkConnectivityManager =
        mockk<NetworkConnectivityManager>().apply {
            every { isInternetValidated } returns MutableStateFlow(false)
        }

    private fun controller(
        announcementRepository: AnnouncementRepository = defaultAnnouncementRepository(),
        userPreferences: UserPreferences = defaultUserPreferences(),
        networkConnectivityManager: NetworkConnectivityManager = defaultNetworkConnectivityManager(),
    ) = EarlySpotterController(announcementRepository, userPreferences, networkConnectivityManager)

    @Test
    fun `onProfileCreated then showCardIfEligible after the tour finishes shows the combined card with number and points`() = runTest {
        val ctrl = controller()

        ctrl.onProfileCreated(isEarlySpotter = true, earlySpotterNumber = 7, bonusPoints = 300)
        assertEquals(EarlySpotterCardState.Hidden, ctrl.state.value)

        ctrl.showCardIfEligible()

        assertEquals(EarlySpotterCardState.Visible(earlySpotterNumber = 7, bonusPoints = 300), ctrl.state.value)
    }

    @Test
    fun `onProfileCreated with isEarlySpotter false never shows the card`() = runTest {
        val ctrl = controller()

        ctrl.onProfileCreated(isEarlySpotter = false, earlySpotterNumber = null, bonusPoints = null)
        ctrl.showCardIfEligible()

        assertEquals(EarlySpotterCardState.Hidden, ctrl.state.value)
    }

    @Test
    fun `after a failed ack, reconnecting retries the ack and the card stays dismissed, not resurrected`() = runTest {
        val userPreferences = mockk<UserPreferences>(relaxed = true)
        every { userPreferences.userId } returns flowOf(userId)

        val dismissedFlow = MutableStateFlow<Set<String>>(emptySet())
        every { userPreferences.earlySpotterDismissed(userId) } returns dismissedFlow
        coEvery { userPreferences.addEarlySpotterDismissed(userId, any()) } coAnswers {
            val key = it.invocation.args[1] as String
            // onAcknowledged() launches WELCOME's and BONUS's acknowledge() as two separate
            // coroutines that can run concurrently on Dispatchers.Default — a plain
            // `flow.value = flow.value + key` read-modify-write would race and lose an update;
            // MutableStateFlow.update {} is the atomic, concurrency-safe way to do this.
            dismissedFlow.update { it + key }
        }

        val pendingAcksFlow = MutableStateFlow<Set<String>>(emptySet())
        every { userPreferences.pendingEarlySpotterAcks(userId) } returns pendingAcksFlow
        coEvery { userPreferences.addPendingEarlySpotterAck(userId, any()) } coAnswers {
            val key = it.invocation.args[1] as String
            pendingAcksFlow.update { it + key }
        }
        coEvery { userPreferences.removePendingEarlySpotterAck(userId, any()) } coAnswers {
            val key = it.invocation.args[1] as String
            pendingAcksFlow.update { it - key }
        }

        val announcementRepository = mockk<AnnouncementRepository>()
        coEvery { announcementRepository.getPending() } returns ApiResult.Success(emptyList())
        coEvery { announcementRepository.acknowledge(EARLY_SPOTTER_WELCOME_KEY) } returns ApiResult.Error("offline")
        coEvery { announcementRepository.acknowledge(EARLY_SPOTTER_BONUS_KEY) } returns ApiResult.Error("offline")

        val isInternetValidated = MutableStateFlow(false)
        val networkConnectivityManager = mockk<NetworkConnectivityManager>()
        every { networkConnectivityManager.isInternetValidated } returns isInternetValidated

        val ctrl = EarlySpotterController(announcementRepository, userPreferences, networkConnectivityManager)
        ctrl.onProfileCreated(isEarlySpotter = true, earlySpotterNumber = 7, bonusPoints = 300)
        ctrl.showCardIfEligible()
        assertEquals(EarlySpotterCardState.Visible(earlySpotterNumber = 7, bonusPoints = 300), ctrl.state.value)

        ctrl.onAcknowledged()
        // Dismissed locally and hidden immediately, independent of the ack's own network result.
        assertEquals(EarlySpotterCardState.Hidden, ctrl.state.value)

        // Both acks failed -> queued for retry. Waiting on acknowledge() alone would race the
        // addPendingEarlySpotterAck() write that follows it in the same coroutine (this all runs
        // on the controller's own Dispatchers.Default scope, not this runTest's) — wait for the
        // queue write itself, the thing retryPendingAcks() below actually depends on.
        coVerify(timeout = 1000) { userPreferences.addPendingEarlySpotterAck(userId, EARLY_SPOTTER_WELCOME_KEY) }
        coVerify(timeout = 1000) { userPreferences.addPendingEarlySpotterAck(userId, EARLY_SPOTTER_BONUS_KEY) }

        // Reconnect: this time the acks succeed, and the server still reports both as PENDING
        // (ack confirmation hasn't landed from the client's point of view before this).
        coEvery { announcementRepository.acknowledge(EARLY_SPOTTER_WELCOME_KEY) } returns ApiResult.Success(Unit)
        coEvery { announcementRepository.acknowledge(EARLY_SPOTTER_BONUS_KEY) } returns ApiResult.Success(Unit)
        coEvery { announcementRepository.getPending() } returns ApiResult.Success(
            listOf(
                AnnouncementDTO(key = EARLY_SPOTTER_WELCOME_KEY, status = "PENDING", payload = """{"earlySpotterNumber":7}"""),
                AnnouncementDTO(key = EARLY_SPOTTER_BONUS_KEY, status = "PENDING", payload = """{"points":300}"""),
            )
        )
        isInternetValidated.value = true

        // The retry happened (past onValidatedReconnect's real debounce, waited out by the timeout).
        coVerify(timeout = 1000, exactly = 2) { announcementRepository.acknowledge(EARLY_SPOTTER_WELCOME_KEY) }
        coVerify(timeout = 1000, exactly = 2) { announcementRepository.acknowledge(EARLY_SPOTTER_BONUS_KEY) }

        // The card was never resurrected — earlySpotterDismissed already covers both keys,
        // regardless of the server still showing them PENDING at that point.
        assertEquals(EarlySpotterCardState.Hidden, ctrl.state.value)
    }
}
