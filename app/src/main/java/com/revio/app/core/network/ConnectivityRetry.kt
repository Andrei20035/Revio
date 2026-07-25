package com.revio.app.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Emits only on the offline -> online transition, ignoring the initial value so subscribers
 * don't trigger an extra load right at startup.
 */
fun NetworkConnectivityManager.onReconnected(): Flow<Unit> =
    isNetworkAvailable
        .drop(1)
        .filter { it }
        .map { }
