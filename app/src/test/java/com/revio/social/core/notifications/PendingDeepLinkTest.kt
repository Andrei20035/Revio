package com.revio.social.core.notifications

import android.content.Intent
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsParamValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mutable [Clock] test double — lets the TTL-expiry test advance "now" between capture and consume. Mirrors NoticesUnreadControllerTest's own copy. */
private class MutableClock(startInstant: Instant) : Clock() {
    var instant: Instant = startInstant
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instant
}

class PendingDeepLinkTest {

    private val analyticsClient: AnalyticsClient = mockk(relaxed = true)

    private fun intentWith(deepLink: String? = null, challengeId: String? = null): Intent =
        mockk<Intent> {
            every { getStringExtra(EXTRA_DEEP_LINK) } returns deepLink
            every { getStringExtra(EXTRA_CHALLENGE_ID) } returns challengeId
        }

    @Test
    fun `capture with deep_link=challenge and a valid challenge_id buffers the correct target`() = runTest {
        val pendingDeepLink = PendingDeepLink(MutableClock(Instant.parse("2026-06-15T12:00:00Z")), analyticsClient)
        val challengeId = UUID.randomUUID()

        pendingDeepLink.capture(intentWith(deepLink = "challenge", challengeId = challengeId.toString()))
        val target = pendingDeepLink.consume()

        assertEquals(DeepLinkDestination.CHALLENGE, target?.destination)
        assertEquals(challengeId, target?.challengeId)
    }

    @Test
    fun `capture with a malformed challenge_id buffers a target with a null challengeId, without throwing`() = runTest {
        val pendingDeepLink = PendingDeepLink(MutableClock(Instant.parse("2026-06-15T12:00:00Z")), analyticsClient)

        pendingDeepLink.capture(intentWith(deepLink = "challenge", challengeId = "not-a-uuid"))
        val target = pendingDeepLink.consume()

        assertEquals(DeepLinkDestination.CHALLENGE, target?.destination)
        assertNull(target?.challengeId)
    }

    @Test
    fun `capture with no deep_link extra is ignored`() = runTest {
        val pendingDeepLink = PendingDeepLink(MutableClock(Instant.parse("2026-06-15T12:00:00Z")), analyticsClient)

        pendingDeepLink.capture(intentWith(deepLink = null))

        assertNull(pendingDeepLink.consume())
    }

    @Test
    fun `consume returns null once the buffered target is older than the 10-minute TTL`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-15T12:00:00Z"))
        val pendingDeepLink = PendingDeepLink(clock, analyticsClient)

        pendingDeepLink.capture(intentWith(deepLink = "challenge", challengeId = UUID.randomUUID().toString()))
        clock.instant = clock.instant.plus(Duration.ofMinutes(11))

        assertNull(pendingDeepLink.consume())
    }

    @Test
    fun `capturing a challenge deep link logs push_opened with category challenge`() = runTest {
        val pendingDeepLink = PendingDeepLink(MutableClock(Instant.parse("2026-06-15T12:00:00Z")), analyticsClient)

        pendingDeepLink.capture(intentWith(deepLink = "challenge", challengeId = UUID.randomUUID().toString()))

        verify {
            analyticsClient.log(
                match { event ->
                    event.name == "push_opened" && event.params["category"] == AnalyticsParamValue.StringValue("challenge")
                },
            )
        }
    }
}
