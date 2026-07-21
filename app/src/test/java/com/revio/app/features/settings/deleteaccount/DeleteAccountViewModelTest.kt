package com.revio.app.features.settings.deleteaccount

import com.revio.app.MainDispatcherRule
import com.revio.app.core.network.ApiResult
import com.revio.app.data.model.AuthProvider
import com.revio.app.data.remote.dto.auth.DeleteAccountRequest
import com.revio.app.data.remote.dto.auth.DeletionContextDto
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
class DeleteAccountViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()

    private fun stats(provider: AuthProvider) = DeletionContextDto(
        provider = provider,
        postCount = 3,
        likesReceived = 5,
        leaderboardRank = 42,
        streakDays = 2,
        accountAgeDays = 10,
    )

    private fun createViewModel(
        contextResult: ApiResult<DeletionContextDto> = ApiResult.Success(stats(AuthProvider.REGULAR)),
    ): DeleteAccountViewModel {
        coEvery { authRepository.getDeletionContext() } returns contextResult
        val vm = DeleteAccountViewModel(authRepository)
        return vm
    }

    // ---- step transitions ----

    @Test
    fun `cannot advance past Reason step without a selected reason`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAction(DeleteAccountAction.NextStep)

        assertEquals(DeleteAccountStep.Reason, vm.uiState.value.currentStep)
    }

    @Test
    fun `full forward and backward navigation across all three steps`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAction(DeleteAccountAction.SelectReason(DeletionReason.TAKING_A_BREAK))
        vm.onAction(DeleteAccountAction.NextStep)
        assertEquals(DeleteAccountStep.Retention, vm.uiState.value.currentStep)

        vm.onAction(DeleteAccountAction.NextStep)
        assertEquals(DeleteAccountStep.Confirm, vm.uiState.value.currentStep)

        vm.onAction(DeleteAccountAction.PreviousStep)
        assertEquals(DeleteAccountStep.Retention, vm.uiState.value.currentStep)

        vm.onAction(DeleteAccountAction.PreviousStep)
        assertEquals(DeleteAccountStep.Reason, vm.uiState.value.currentStep)

        // Already at the first step — previous is a no-op.
        vm.onAction(DeleteAccountAction.PreviousStep)
        assertEquals(DeleteAccountStep.Reason, vm.uiState.value.currentStep)
    }

    @Test
    fun `deletion-context failure still allows reaching the Confirm step`() = runTest {
        val vm = createViewModel(contextResult = ApiResult.Error("network error"))
        advanceUntilIdle()

        vm.onAction(DeleteAccountAction.SelectReason(DeletionReason.OTHER))
        vm.onAction(DeleteAccountAction.NextStep)
        vm.onAction(DeleteAccountAction.NextStep)

        assertEquals(DeleteAccountStep.Confirm, vm.uiState.value.currentStep)
        assertNull(vm.uiState.value.stats)
        assertFalse(vm.uiState.value.isLoading)
    }

    // ---- reason / details ----

    @Test
    fun `OTHER reason sends the typed details, other reasons send null details`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { authRepository.deleteAccount(any()) } returns ApiResult.Success(Unit)

        vm.onAction(DeleteAccountAction.SelectReason(DeletionReason.OTHER))
        vm.onAction(DeleteAccountAction.OtherTextChanged("Too many bugs"))
        vm.onAction(DeleteAccountAction.PasswordChanged("Passw0rd!"))
        vm.onAction(DeleteAccountAction.Confirm)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            authRepository.deleteAccount(
                DeleteAccountRequest(
                    password = "Passw0rd!",
                    usernameConfirmation = null,
                    reason = "OTHER",
                    details = "Too many bugs",
                ),
            )
        }
    }

    @Test
    fun `non-OTHER reason never sends details even if otherText was previously typed`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { authRepository.deleteAccount(any()) } returns ApiResult.Success(Unit)

        vm.onAction(DeleteAccountAction.SelectReason(DeletionReason.TAKING_A_BREAK))
        vm.onAction(DeleteAccountAction.PasswordChanged("Passw0rd!"))
        vm.onAction(DeleteAccountAction.Confirm)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            authRepository.deleteAccount(
                DeleteAccountRequest(
                    password = "Passw0rd!",
                    usernameConfirmation = null,
                    reason = "TAKING_A_BREAK",
                    details = null,
                ),
            )
        }
    }

    // ---- provider ----

    @Test
    fun `REGULAR provider from deletion-context sends the password field`() = runTest {
        val vm = createViewModel(contextResult = ApiResult.Success(stats(AuthProvider.REGULAR)))
        advanceUntilIdle()

        assertEquals(AuthProvider.REGULAR, vm.uiState.value.provider)

        coEvery { authRepository.deleteAccount(any()) } returns ApiResult.Success(Unit)
        vm.onAction(DeleteAccountAction.SelectReason(DeletionReason.OTHER))
        vm.onAction(DeleteAccountAction.PasswordChanged("Passw0rd!"))
        vm.onAction(DeleteAccountAction.Confirm)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            authRepository.deleteAccount(match { it.password == "Passw0rd!" && it.usernameConfirmation == null })
        }
    }

    @Test
    fun `GOOGLE provider from deletion-context sends the username confirmation field`() = runTest {
        val vm = createViewModel(contextResult = ApiResult.Success(stats(AuthProvider.GOOGLE)))
        advanceUntilIdle()

        assertEquals(AuthProvider.GOOGLE, vm.uiState.value.provider)

        coEvery { authRepository.deleteAccount(any()) } returns ApiResult.Success(Unit)
        vm.onAction(DeleteAccountAction.SelectReason(DeletionReason.OTHER))
        vm.onAction(DeleteAccountAction.UsernameConfirmationChanged("bob"))
        vm.onAction(DeleteAccountAction.Confirm)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            authRepository.deleteAccount(match { it.usernameConfirmation == "bob" && it.password == null })
        }
    }

    // ---- error mapping ----

    @Test
    fun `INVALID_CURRENT_PASSWORD maps to confirmFieldError and deletionCompleted stays false`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { authRepository.deleteAccount(any()) } returns
            ApiResult.Error("Invalid password", code = "INVALID_CURRENT_PASSWORD")

        vm.onAction(DeleteAccountAction.SelectReason(DeletionReason.OTHER))
        vm.onAction(DeleteAccountAction.PasswordChanged("wrong"))
        vm.onAction(DeleteAccountAction.Confirm)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Invalid password", state.confirmFieldError)
        assertNull(state.generalError)
        assertFalse(state.deletionCompleted)
        assertFalse(state.isDeleting)
    }

    @Test
    fun `USERNAME_CONFIRMATION_MISMATCH maps to confirmFieldError`() = runTest {
        val vm = createViewModel(contextResult = ApiResult.Success(stats(AuthProvider.GOOGLE)))
        advanceUntilIdle()
        coEvery { authRepository.deleteAccount(any()) } returns
            ApiResult.Error("Username does not match", code = "USERNAME_CONFIRMATION_MISMATCH")

        vm.onAction(DeleteAccountAction.SelectReason(DeletionReason.OTHER))
        vm.onAction(DeleteAccountAction.UsernameConfirmationChanged("not-bob"))
        vm.onAction(DeleteAccountAction.Confirm)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Username does not match", state.confirmFieldError)
        assertFalse(state.deletionCompleted)
    }

    @Test
    fun `unrecognised error code maps to generalError`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { authRepository.deleteAccount(any()) } returns
            ApiResult.Error("Something went wrong", code = null)

        vm.onAction(DeleteAccountAction.SelectReason(DeletionReason.OTHER))
        vm.onAction(DeleteAccountAction.PasswordChanged("Passw0rd!"))
        vm.onAction(DeleteAccountAction.Confirm)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Something went wrong", state.generalError)
        assertNull(state.confirmFieldError)
        assertFalse(state.deletionCompleted)
    }

    // ---- success ----

    @Test
    fun `successful deletion sets deletionCompleted to true`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { authRepository.deleteAccount(any()) } returns ApiResult.Success(Unit)

        vm.onAction(DeleteAccountAction.SelectReason(DeletionReason.OTHER))
        vm.onAction(DeleteAccountAction.PasswordChanged("Passw0rd!"))
        vm.onAction(DeleteAccountAction.Confirm)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.deletionCompleted)
        assertFalse(state.isDeleting)
    }
}
