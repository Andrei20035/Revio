package com.revio.app.features.settings.changepassword

import com.revio.app.MainDispatcherRule
import com.revio.app.core.network.ApiResult
import com.revio.app.data.remote.dto.auth.AuthResponse
import com.revio.app.data.remote.dto.auth.OnboardingStep
import com.revio.app.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()

    private fun authResponse() = AuthResponse(
        accessToken = "new-access-token",
        onboardingStep = OnboardingStep.COMPLETED,
    )

    private fun createViewModel() = ChangePasswordViewModel(authRepository)

    @Test
    fun `onSave success clears sensitive fields and marks saveSuccess`() = runTest {
        val vm = createViewModel()
        coEvery { authRepository.updatePassword("OldPass1!", "NewPass1!") } returns
            ApiResult.Success(authResponse())

        vm.onOldPasswordChanged("OldPass1!")
        vm.onNewPasswordChanged("NewPass1!")
        vm.onConfirmPasswordChanged("NewPass1!")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.saveSuccess)
        assertFalse(state.isSaving)
        assertEquals("", state.oldPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
        coVerify(exactly = 1) { authRepository.updatePassword("OldPass1!", "NewPass1!") }
    }

    @Test
    fun `onSave maps INVALID_CURRENT_PASSWORD to the old password field and keeps values`() = runTest {
        val vm = createViewModel()
        coEvery { authRepository.updatePassword("WrongPass1!", "NewPass1!") } returns
            ApiResult.Error("Invalid current password", code = "INVALID_CURRENT_PASSWORD")

        vm.onOldPasswordChanged("WrongPass1!")
        vm.onNewPasswordChanged("NewPass1!")
        vm.onConfirmPasswordChanged("NewPass1!")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Invalid current password", state.oldPasswordError)
        assertNull(state.newPasswordError)
        assertNull(state.generalError)
        assertEquals("WrongPass1!", state.oldPassword)
        assertEquals("NewPass1!", state.newPassword)
        assertFalse(state.saveSuccess)
    }

    @Test
    fun `onSave maps WEAK_PASSWORD to the new password field`() = runTest {
        val vm = createViewModel()
        coEvery { authRepository.updatePassword("OldPass1!", "NewPass1!") } returns
            ApiResult.Error("Password does not meet requirements", code = "WEAK_PASSWORD")

        vm.onOldPasswordChanged("OldPass1!")
        vm.onNewPasswordChanged("NewPass1!")
        vm.onConfirmPasswordChanged("NewPass1!")
        vm.onSave()
        advanceUntilIdle()

        assertNull(vm.uiState.value.oldPasswordError)
        assertEquals("Password does not meet requirements", vm.uiState.value.newPasswordError)
    }

    @Test
    fun `onSave maps PROVIDER_NOT_REGULAR to a general error`() = runTest {
        val vm = createViewModel()
        coEvery { authRepository.updatePassword("OldPass1!", "NewPass1!") } returns
            ApiResult.Error("Password cannot be updated for this provider", code = "PROVIDER_NOT_REGULAR")

        vm.onOldPasswordChanged("OldPass1!")
        vm.onNewPasswordChanged("NewPass1!")
        vm.onConfirmPasswordChanged("NewPass1!")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(
            "You signed in with Google, so there's no password to change.",
            state.generalError,
        )
        assertNull(state.oldPasswordError)
        assertNull(state.newPasswordError)
    }

    @Test
    fun `onSave is blocked when new and confirm passwords do not match`() = runTest {
        val vm = createViewModel()

        vm.onOldPasswordChanged("OldPass1!")
        vm.onNewPasswordChanged("NewPass1!")
        vm.onConfirmPasswordChanged("Different1!")

        assertTrue(vm.uiState.value.isSaveBlocked)
        vm.onSave()
        advanceUntilIdle()

        coVerify(exactly = 0) { authRepository.updatePassword(any(), any()) }
    }

    @Test
    fun `onSave is blocked when new password does not meet requirements`() = runTest {
        val vm = createViewModel()

        vm.onOldPasswordChanged("OldPass1!")
        vm.onNewPasswordChanged("weak")
        vm.onConfirmPasswordChanged("weak")

        assertTrue(vm.uiState.value.isSaveBlocked)
        vm.onSave()
        advanceUntilIdle()

        coVerify(exactly = 0) { authRepository.updatePassword(any(), any()) }
    }
}
