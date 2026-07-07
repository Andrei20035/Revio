package com.revio.app.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val POST_DATE_FORMATTER = DateTimeFormatter
    .ofPattern("d MMM yyyy", Locale.ENGLISH)
    .withZone(ZoneId.systemDefault())

/** Formats an instant as "12 Mar 2026". */
fun Instant.toPostDate(): String = POST_DATE_FORMATTER.format(this)
