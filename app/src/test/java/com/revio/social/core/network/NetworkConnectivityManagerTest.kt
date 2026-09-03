package com.revio.social.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * pas 1 (docs/plans/avem-un-bug-android-mutable-sky.md) — `refresh()` is now public so a
 * foreground resume can correct a value left stale by a missed/delayed system callback. These
 * tests exercise `refresh()` directly against a mocked [ConnectivityManager], without going
 * through the real [android.net.ConnectivityManager.registerDefaultNetworkCallback] callback.
 */
class NetworkConnectivityManagerTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        connectivityManager = mockk()
        context = mockk {
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        }
        every { connectivityManager.registerDefaultNetworkCallback(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun onlineCapabilities(): NetworkCapabilities = mockk {
        every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { hasTransport(any()) } returns false
        every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
    }

    @Test
    fun `refresh dupa ce activeNetwork devine non-null trece flow-ul pe true`() = runTest {
        // init(): no active network yet.
        every { connectivityManager.activeNetwork } returns null

        val manager = NetworkConnectivityManager(context)
        assertFalse(manager.isNetworkAvailable.value)

        // A network becomes active with full internet capabilities.
        val network = mockk<Network>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns onlineCapabilities()

        manager.isNetworkAvailable.test {
            assertEquals(false, awaitItem())
            manager.refresh(source = "resume")
            assertEquals(true, awaitItem())
        }
        assertTrue(manager.isNetworkAvailable.value)
    }

    @Test
    fun `refresh fara schimbare nu produce a doua emisie`() = runTest {
        val network = mockk<Network>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns onlineCapabilities()

        // init() already observes the online network above.
        val manager = NetworkConnectivityManager(context)
        assertTrue(manager.isNetworkAvailable.value)

        manager.isNetworkAvailable.test {
            assertEquals(true, awaitItem())
            // Same underlying state — refresh() must not cause a second, redundant emission.
            manager.refresh(source = "resume")
            manager.refresh(source = "resume")
            expectNoEvents()
        }
    }

    @Test
    fun `refresh apeleaza breadcrumb fara continut sensibil`() = runTest {
        every { connectivityManager.activeNetwork } returns null

        val manager = NetworkConnectivityManager(context)

        val logged: CapturingSlot<String> = slot()
        verify { crashlytics.log(capture(logged)) }
        assertTrue(logged.captured.contains("source=init"))
        assertFalse(logged.captured.contains("?"))
    }
}
