package com.revio.app.core.util

import java.time.Duration
import java.time.Instant

/**
 * Formats an instant as a coarse, Instagram-style relative timestamp:
 *  - < 1 hour  → "N minute(s) ago"
 *  - < 24 hours → "N hour(s) ago"
 *  - otherwise  → "N day(s) ago"
 *
 * Anything under a minute (or a future timestamp from minor clock skew) reads "just now".
 * Singular/plural is handled ("1 minute ago" vs "2 minutes ago").
 */
fun Instant.toRelativeTime(now: Instant = Instant.now()): String {
    val seconds = Duration.between(this, now).seconds
    if (seconds < 60) return "just now"

    val minutes = seconds / 60
    if (minutes < 60) return "$minutes ${plural(minutes, "minute")} ago"

    val hours = minutes / 60
    if (hours < 24) return "$hours ${plural(hours, "hour")} ago"

    val days = hours / 24
    return "$days ${plural(days, "day")} ago"
}

private fun plural(value: Long, unit: String): String = if (value == 1L) unit else "${unit}s"
