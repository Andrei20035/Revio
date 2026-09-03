package com.revio.social.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.revio.social.core.analytics.CrashContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A manager for monitoring network connectivity changes.
 * Provides StateFlows that emit the current network connectivity status.
 */
@Singleton
class NetworkConnectivityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isNetworkAvailable = MutableStateFlow(false)
    private val _isInternetValidated = MutableStateFlow(false)
    private val _connectionType = MutableStateFlow<String?>(null)

    /**
     * A StateFlow that emits the current network connectivity status.
     * True if the device has an active network connection, false otherwise.
     */
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    /**
     * True only once the system has confirmed the active network actually reaches the internet
     * ([NetworkCapabilities.NET_CAPABILITY_VALIDATED], in addition to [NetworkCapabilities.NET_CAPABILITY_INTERNET]).
     * A captive portal or a Wi-Fi network with no uplink reports [isNetworkAvailable] but never
     * validates. Intended for UI copy and auto-retry only — never for gating requests, because
     * VALIDATED briefly lags behind INTERNET right after a network connects, which would reject
     * perfectly good calls exactly when a reconnect-triggered retry fires.
     */
    val isInternetValidated: StateFlow<Boolean> = _isInternetValidated.asStateFlow()

    /**
     * The transport of the active network: `"wifi"`, `"cellular"`, `"ethernet"`, `"other"`, or
     * `null` when there is no active network. Intended for diagnostic context sent with user
     * feedback reports, not for gating requests.
     */
    val connectionType: StateFlow<String?> = _connectionType.asStateFlow()

    init {
        refresh(source = "init")

        // Tracks the network the system actually routes through, matching the semantics of
        // `activeNetwork` used below — a capability-filtered request would instead fire for
        // every matching network, not just the one in active use.
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh(source = "onAvailable")

            override fun onLost(network: Network) = refresh(source = "onLost")

            // The only callback where the INTERNET -> VALIDATED transition surfaces.
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
                refresh(source = "onCapabilitiesChanged")
        })
    }

    /**
     * Re-reads [ConnectivityManager.getActiveNetwork] / capabilities synchronously and updates
     * the cached state. Public so callers outside this class can force a re-check: [source]
     * `"resume"`, called on app foreground (pas 1, docs/plans/avem-un-bug-android-mutable-sky.md),
     * to correct a cached value left stale by a missed/delayed system callback while the process
     * was backgrounded; and [source] `"interceptor"` (pas 2), called from
     * [NetworkConnectivityInterceptor] before it trusts a cached `false` enough to reject a
     * request. (pas 0's [source]-tagged breadcrumb below covers the other, callback-driven
     * sources: "init", "onAvailable", "onLost", "onCapabilitiesChanged".) Idempotent and cheap
     * (two binder calls); the [MutableStateFlow]s below don't re-emit when the value is
     * unchanged, so a call with no real change triggers no extra retries downstream.
     */
    fun refresh(source: String) {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = hasInternet && capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        _isNetworkAvailable.value = hasInternet
        _isInternetValidated.value = isValidated
        _connectionType.value = when {
            capabilities == null -> null
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }

        // No tokens, no user data, no query strings — see the [source] doc comment above.
        CrashContext.breadcrumb(
            "connectivity_refresh source=$source activeNetwork=${network != null} " +
                "hasInternet=$hasInternet isValidated=$isValidated transport=${_connectionType.value}"
        )
    }
}
