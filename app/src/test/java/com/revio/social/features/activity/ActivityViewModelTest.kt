package com.revio.social.features.activity

import com.revio.social.MainDispatcherRule
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.data.repository.ActivityRepository
import com.revio.social.data.repository.UserRepository
import com.revio.social.features.activity.model.ActivityData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val repository: ActivityRepository = mockk()
    private val userRepository: UserRepository = mockk {
        every { currentUser } returns MutableStateFlow(null)
        coEvery { getCurrentUser() } returns ApiResult.Error("Server error")
    }
    private val networkAvailable = MutableStateFlow(true)
    private val connectivity: NetworkConnectivityManager = mockk {
        every { isNetworkAvailable } returns networkAvailable
    }

    private val successResult = ActivityData(
        weeklySpotScore = 42,
        todayInteractions = 3,
        items = emptyList(),
    )

    @Test
    fun `reconnecting after a network error reloads automatically`() = runTest {
        networkAvailable.value = false
        coEvery { repository.getActivity() } returnsMany listOf(
            ApiResult.Error("Network error", code = "network_unavailable"),
            ApiResult.Success(successResult),
        )
        val vm = ActivityViewModel(repository, userRepository, connectivity)
        advanceUntilIdle()

        networkAvailable.value = true
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.errorMessage)
        assertEquals(42, state.weeklySpotScore)
        coVerify(exactly = 2) { repository.getActivity() }
    }

    @Test
    fun `reconnecting does not reload when there was no error`() = runTest {
        networkAvailable.value = false
        coEvery { repository.getActivity() } returns ApiResult.Success(successResult)
        val vm = ActivityViewModel(repository, userRepository, connectivity)
        advanceUntilIdle()

        networkAvailable.value = true
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getActivity() }
    }
}
