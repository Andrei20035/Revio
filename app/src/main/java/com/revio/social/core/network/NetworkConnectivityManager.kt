package com.revio.social.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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

    init {
        refresh()

        // Tracks the network the system actually routes through, matching the semantics of
        // `activeNetwork` used below — a capability-filtered request would instead fire for
        // every matching network, not just the one in active use.
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()

            override fun onLost(network: Network) = refresh()

            // The only callback where the INTERNET -> VALIDATED transition surfaces.
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
        })
    }

    private fun refresh() {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = hasInternet && capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        _isNetworkAvailable.value = hasInternet
        _isInternetValidated.value = isValidated
    }
}
