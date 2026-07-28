package com.revio.social.core.network

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

private const val VALIDATED_RECONNECT_DEBOUNCE_MS = 400L

/**
 * Emits only on the offline -> online transition, ignoring the initial value so subscribers
 * don't trigger an extra load right at startup.
 */
fun NetworkConnectivityManager.onReconnected(): Flow<Unit> =
    isNetworkAvailable
        .drop(1)
        .filter { it }
        .map { }

/**
 * Same shape as [onReconnected], but keyed on [NetworkConnectivityManager.isInternetValidated]
 * rather than raw connectivity, and debounced so a flapping connection (train tunnel, elevator)
 * doesn't fire one reload per blip.
 */
@OptIn(FlowPreview::class)
fun NetworkConnectivityManager.onValidatedReconnect(): Flow<Unit> =
    isInternetValidated
        .drop(1)
        .filter { it }
        .debounce(VALIDATED_RECONNECT_DEBOUNCE_MS)
        .map { }
