package com.revio.social.features.admin.challenge.create

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Offset-less ISO-8601, e.g. `"2026-08-12T09:00:00"` — the exact shape the server's
 * `LocalDateTime.parse` expects for `startsAtLocal`/`endsAtLocal` (`ChallengeAdminRoutes.kt:131`).
 * Truncated to seconds first so a picker that only sets hour/minute never leaks a stray
 * sub-second fraction into the request body. */
private val CHALLENGE_LOCAL_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

fun LocalDateTime.toChallengeLocalIsoString(): String =
    this.withNano(0).format(CHALLENGE_LOCAL_DATE_TIME_FORMATTER)
