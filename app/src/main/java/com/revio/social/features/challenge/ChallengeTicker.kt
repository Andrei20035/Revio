package com.revio.social.features.challenge

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits `clock.instant()` immediately, then again at every following minute boundary. Each
 * wait is computed from the actual clock rather than a fixed `delay(60_000)` repeated, so the
 * cadence can't drift away from real minute boundaries over time.
 */
fun minuteTicker(clock: Clock): Flow<Instant> = flow {
    while (true) {
        val now = clock.instant()
        emit(now)
        val millisIntoMinute = now.toEpochMilli() % 60_000
        delay(60_000 - millisIntoMinute)
    }
}
