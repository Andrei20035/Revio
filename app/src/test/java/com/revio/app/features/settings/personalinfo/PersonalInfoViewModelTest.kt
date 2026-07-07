package com.revio.app.features.settings.personalinfo

import com.revio.app.MainDispatcherRule
import com.revio.app.core.image.ImageCompressor
import com.revio.app.core.network.ApiResult
import com.revio.app.data.model.User
import com.revio.app.data.remote.dto.user.UpdateUserRequest
import com.revio.app.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalInfoViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val userRepository: UserRepository = mockk()
    private val imageCompressor: ImageCompressor = mockk(relaxed = true)

    private fun user() = User(
        id = UUID.randomUUID(),
        fullName = "Alice",
        username = "alice",
        country = "Romania",
        birthDate = LocalDate.of(1995, 1, 1),
        phoneNumber = "+40700000000",
    )

    private fun createViewModel(initialUser: User = user()): PersonalInfoViewModel {
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(initialUser)
        val vm = PersonalInfoViewModel(userRepository, imageCompressor)
        return vm
    }

    @Test
    fun `onSave sends only the changed field`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { userRepository.updateUser(any()) } returns ApiResult.Success(user().copy(username = "newname"))

        vm.onUsernameChanged("newname")
        vm.onSave()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            userRepository.updateUser(
                UpdateUserRequest(username = "newname"),
            )
        }
    }

    @Test
    fun `onSave with no changes does not call the repository`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSave()
        advanceUntilIdle()

        coVerify(exactly = 0) { userRepository.updateUser(any()) }
    }

    @Test
    fun `onSave success updates user and clears errors`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val updatedUser = user().copy(fullName = "Alice New")
        coEvery { userRepository.updateUser(any()) } returns ApiResult.Success(updatedUser)

        vm.onFullNameChanged("Alice New")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.saveSuccess)
        assertEquals("Alice New", state.user?.fullName)
        assertTrue(state.fieldErrors.isEmpty())
        assertNull(state.generalError)
    }

    @Test
    fun `onSave maps 409 username conflict to the username field`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { userRepository.updateUser(any()) } returns ApiResult.Error("Username is already taken")

        vm.onUsernameChanged("bob")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Username is already taken", state.fieldErrors[PersonalInfoField.USERNAME])
        assertNull(state.generalError)
    }

    @Test
    fun `onSave maps phone format error to the phone number field`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { userRepository.updateUser(any()) } returns
            ApiResult.Error("Phone number must be at most 20 characters")

        vm.onPhoneNumberChanged("+407000000001234567890")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(
            "Phone number must be at most 20 characters",
            state.fieldErrors[PersonalInfoField.PHONE_NUMBER],
        )
    }

    @Test
    fun `onSave maps one-time change rejection to the full name field`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { userRepository.updateUser(any()) } returns
            ApiResult.Error("Full name can only be changed once", code = "FULL_NAME_ALREADY_CHANGED")

        vm.onFullNameChanged("New Alice")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(
            "Full name can only be changed once",
            state.fieldErrors[PersonalInfoField.FULL_NAME],
        )
    }

    @Test
    fun `onSave falls back to generalError when the message cannot be mapped to a field`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { userRepository.updateUser(any()) } returns ApiResult.Error("Unexpected server error")

        vm.onFullNameChanged("Someone Else")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.fieldErrors.isEmpty())
        assertEquals("Unexpected server error", state.generalError)
    }
}
