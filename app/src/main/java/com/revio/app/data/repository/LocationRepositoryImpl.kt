package com.revio.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.revio.app.data.model.Coordinates
import com.revio.app.data.model.PlaceName
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Best-effort, foreground-only location for the post flow. Every method is nullable and
 * non-throwing: missing permission, disabled location, no fix, geocoder failure, or a timeout
 * all simply yield null so the caller can post without location.
 */
interface LocationRepository {
    /** True if fine OR coarse foreground location permission is currently granted. */
    fun hasLocationPermission(): Boolean

    /** One-shot device location, or null if unavailable within the timeout / without permission. */
    suspend fun getCurrentLocation(): Coordinates?

    /** Reverse-geocode coordinates to a town/country via Android's [Geocoder]; null on failure. */
    suspend fun reverseGeocode(coordinates: Coordinates): PlaceName?
}

@Singleton
class LocationRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationRepository {

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private val hasFine get() = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
    private val hasCoarse get() = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun hasLocationPermission(): Boolean = hasFine || hasCoarse

    override suspend fun getCurrentLocation(): Coordinates? {
        if (!hasLocationPermission()) return null
        // Precise fix when fine is granted; coarse-only still works at balanced power.
        val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            try {
                val location = awaitCurrentLocation(priority) ?: awaitLastLocation()
                location?.let { Coordinates(it.latitude, it.longitude) }
            } catch (_: SecurityException) {
                null
            }
        }
    }

    private suspend fun awaitCurrentLocation(priority: Int): Location? {
        val cts = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            fusedClient.getCurrentLocation(priority, cts.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        }
    }

    private suspend fun awaitLastLocation(): Location? =
        suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }

    override suspend fun reverseGeocode(coordinates: Coordinates): PlaceName? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())

        val addresses: List<Address>? = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    awaitGeocode(geocoder, coordinates)
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1)
                    }
                }
            }.getOrNull()
        }

        val address = addresses?.firstOrNull() ?: return null
        val town = address.locality ?: address.subAdminArea ?: address.adminArea
        val country = address.countryName
        // Nothing useful resolved → treat as no place name.
        if (town == null && country == null) return null
        return PlaceName(town = town, country = country)
    }

    private suspend fun awaitGeocode(geocoder: Geocoder, coordinates: Coordinates): List<Address>? =
        suspendCancellableCoroutine { cont ->
            geocoder.getFromLocation(
                coordinates.latitude,
                coordinates.longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        cont.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        cont.resume(null)
                    }
                },
            )
        }

    companion object {
        private const val LOCATION_TIMEOUT_MS = 4_000L
        private const val GEOCODE_TIMEOUT_MS = 3_000L
    }
}
