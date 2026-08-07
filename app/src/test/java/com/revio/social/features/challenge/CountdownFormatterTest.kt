package com.revio.social.features.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.TimeZone

class CountdownFormatterTest {

    private val now = Instant.parse("2026-08-07T12:00:00Z")

    private fun endsAtAfter(duration: Duration): Instant = now.plus(duration)

    @Test
    fun `exact 24h00m ramane pe ramura orelor - 24h 00m remaining`() {
        val remaining = remainingTimeAt(now, endsAtAfter(Duration.ofHours(24)))
        assertEquals(RemainingTime.HoursMinutes(24, 0), remaining)
        assertEquals("24h 00m remaining", remaining.label())
    }

    @Test
    fun `23h59m - 23h 59m remaining`() {
        val remaining = remainingTimeAt(now, endsAtAfter(Duration.ofHours(23).plusMinutes(59)))
        assertEquals(RemainingTime.HoursMinutes(23, 59), remaining)
        assertEquals("23h 59m remaining", remaining.label())
    }

    @Test
    fun `25h trece pe ramura zilelor, rotunjit in sus - 2 days remaining`() {
        val remaining = remainingTimeAt(now, endsAtAfter(Duration.ofHours(25)))
        assertEquals(RemainingTime.Days(2), remaining)
        assertEquals("2 days remaining", remaining.label())
    }

    @Test
    fun `47h30m - 2 days remaining`() {
        val remaining = remainingTimeAt(now, endsAtAfter(Duration.ofHours(47).plusMinutes(30)))
        assertEquals(RemainingTime.Days(2), remaining)
        assertEquals("2 days remaining", remaining.label())
    }

    @Test
    fun `5h08m - 5h 08m remaining, minutele cu padding`() {
        val remaining = remainingTimeAt(now, endsAtAfter(Duration.ofHours(5).plusMinutes(8)))
        assertEquals(RemainingTime.HoursMinutes(5, 8), remaining)
        assertEquals("5h 08m remaining", remaining.label())
    }

    @Test
    fun `0h17m - 0h 17m remaining`() {
        val remaining = remainingTimeAt(now, endsAtAfter(Duration.ofMinutes(17)))
        assertEquals(RemainingTime.HoursMinutes(0, 17), remaining)
        assertEquals("0h 17m remaining", remaining.label())
    }

    @Test
    fun `sub 1 minut - 0h 00m remaining, nu coboram la secunde`() {
        val remaining = remainingTimeAt(now, endsAtAfter(Duration.ofSeconds(30)))
        assertEquals(RemainingTime.HoursMinutes(0, 0), remaining)
        assertEquals("0h 00m remaining", remaining.label())
    }

    @Test
    fun `endsAt exact acum - Expired, fara text`() {
        val remaining = remainingTimeAt(now, now)
        assertEquals(RemainingTime.Expired, remaining)
        assertNull(remaining.label())
    }

    @Test
    fun `endsAt in trecut - Expired, fara text`() {
        val remaining = remainingTimeAt(now, now.minusSeconds(10))
        assertEquals(RemainingTime.Expired, remaining)
        assertNull(remaining.label())
    }

    @Test
    fun `rezultatul e identic indiferent de fusul orar al dispozitivului`() {
        val endsAt = endsAtAfter(Duration.ofHours(5).plusMinutes(8))
        val defaultZone = TimeZone.getDefault()
        try {
            val results = listOf("UTC", "America/Los_Angeles", "Asia/Tokyo", "Pacific/Kiritimati").map { zoneId ->
                TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
                remainingTimeAt(now, endsAt)
            }
            assertTrue(results.all { it == results.first() })
            assertEquals(RemainingTime.HoursMinutes(5, 8), results.first())
        } finally {
            TimeZone.setDefault(defaultZone)
        }
    }

    @Test
    fun `label pluralizeaza corect - 1 day vs N days`() {
        assertEquals("1 day remaining", RemainingTime.Days(1).label())
        assertEquals("2 days remaining", RemainingTime.Days(2).label())
        assertEquals("5 days remaining", RemainingTime.Days(5).label())
    }
}
