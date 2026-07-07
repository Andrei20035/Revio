package com.revio.app.data.model

/** A device GPS fix. */
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

/** Best-effort reverse-geocoded place name; either field may be null. */
data class PlaceName(
    val town: String?,
    val country: String?,
)
