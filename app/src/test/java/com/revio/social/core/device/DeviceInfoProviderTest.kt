package com.revio.social.core.device

import com.revio.social.core.network.NetworkConnectivityManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeviceInfoProviderTest {

    @Test
    fun `collect returns non-null diagnostics fields and the current connection type`() {
        val networkConnectivityManager = mockk<NetworkConnectivityManager>()
        every { networkConnectivityManager.connectionType } returns MutableStateFlow("wifi")

        val info = DeviceInfoProvider(networkConnectivityManager).collect()

        assertNotNull(info.appVersion)
        assertNotNull(info.androidVersion)
        assertNotNull(info.deviceModel)
        assertEquals("wifi", info.connectionType)
    }

    @Test
    fun `collect reflects a null connection type when offline`() {
        val networkConnectivityManager = mockk<NetworkConnectivityManager>()
        every { networkConnectivityManager.connectionType } returns MutableStateFlow(null)

        val info = DeviceInfoProvider(networkConnectivityManager).collect()

        assertEquals(null, info.connectionType)
    }
}
