package com.revio.app.features.settings.personalinfo

import com.revio.app.MainDispatcherRule
import com.revio.app.core.image.ImageCompressor
import com.revio.app.core.network.ApiResult
import com.revio.app.data.model.User
import com.revio.app.data.remote.dto.user.UpdateUserRequest
import com.revio.app.data.remote.dto.user.UsernameAvailabilityResponse
import com.revio.app.data.repository.UserRepository
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
    fun `initial state shows loading before the current user is available`() = runTest {
        val pendingUser = kotlinx.coroutines.CompletableDeferred<ApiResult<User>>()
        coEvery { userRepository.getCurrentUser() } coAnswers { pendingUser.await() }

        val vm = PersonalInfoViewModel(userRepository, imageCompressor)

        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.user)

        pendingUser.complete(ApiResult.Success(user()))
        advanceUntilIdle()
    }

    @Test
    fun `initial load error keeps the form unavailable and retry loads it`() = runTest {
        val loadedUser = user()
        coEvery { userRepository.getCurrentUser() } returnsMany listOf(
            ApiResult.Error("Network error"),
            ApiResult.Success(loadedUser),
        )
        val vm = PersonalInfoViewModel(userRepository, imageCompressor)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.user)
        assertEquals("Network error", vm.uiState.value.generalError)

        vm.retryLoadCurrentUser()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(loadedUser, vm.uiState.value.user)
        assertNull(vm.uiState.value.generalError)
        coVerify(exactly = 2) { userRepository.getCurrentUser() }
    }

    @Test
    fun `onSave sends only the changed field`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { userRepository.checkUsernameAvailability("newname") } returns
            ApiResult.Success(UsernameAvailabilityResponse(available = true, normalized = "newname"))
        coEvery { userRepository.updateUser(any()) } returns ApiResult.Success(user().copy(username = "newname"))

        vm.onUsernameChanged("newname")
        advanceUntilIdle()
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
        coEvery { userRepository.checkUsernameAvailability("bob") } returns
            ApiResult.Success(UsernameAvailabilityResponse(available = true, normalized = "bob"))
        coEvery { userRepository.updateUser(any()) } returns ApiResult.Error("Username is already taken")

        vm.onUsernameChanged("bob")
        advanceUntilIdle()
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

    @Test
    fun `rapid username keystrokes debounce into a single availability check for the final value`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { userRepository.checkUsernameAvailability("bobby") } returns
            ApiResult.Success(UsernameAvailabilityResponse(available = true, normalized = "bobby"))

        vm.onUsernameChanged("bob")
        vm.onUsernameChanged("bobb")
        vm.onUsernameChanged("bobby")
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.checkUsernameAvailability("bobby") }
        coVerify(exactly = 0) { userRepository.checkUsernameAvailability("bob") }
        coVerify(exactly = 0) { userRepository.checkUsernameAvailability("bobb") }
        assertEquals(UsernameCheckState.Available, vm.uiState.value.usernameCheck)
    }

    @Test
    fun `a stale username check response is ignored once the user has typed a different value`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        val bobResult = kotlinx.coroutines.CompletableDeferred<ApiResult<UsernameAvailabilityResponse>>()
        coEvery { userRepository.checkUsernameAvailability("bob") } coAnswers { bobResult.await() }
        coEvery { userRepository.checkUsernameAvailability("carl") } returns
            ApiResult.Success(UsernameAvailabilityResponse(available = false, normalized = "carl"))

        vm.onUsernameChanged("bob")
        advanceUntilIdle()
        assertEquals(UsernameCheckState.Checking, vm.uiState.value.usernameCheck)

        vm.onUsernameChanged("carl")
        advanceUntilIdle()

        bobResult.complete(ApiResult.Success(UsernameAvailabilityResponse(available = true, normalized = "bob")))
        advanceUntilIdle()

        assertEquals(UsernameCheckState.Taken, vm.uiState.value.usernameCheck)
        assertEquals("carl", vm.uiState.value.username)
    }

    @Test
    fun `Save is blocked while username availability is Checking, Invalid, or Taken`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onUsernameChanged("ab")
        assertTrue(vm.uiState.value.isSaveBlocked)

        coEvery { userRepository.checkUsernameAvailability("bob") } returns
            ApiResult.Success(UsernameAvailabilityResponse(available = false, normalized = "bob"))
        vm.onUsernameChanged("bob")
        assertTrue(vm.uiState.value.isSaveBlocked)
        advanceUntilIdle()
        assertEquals(UsernameCheckState.Taken, vm.uiState.value.usernameCheck)
        assertTrue(vm.uiState.value.isSaveBlocked)

        coEvery { userRepository.checkUsernameAvailability("carl") } returns
            ApiResult.Success(UsernameAvailabilityResponse(available = true, normalized = "carl"))
        vm.onUsernameChanged("carl")
        advanceUntilIdle()
        assertEquals(UsernameCheckState.Available, vm.uiState.value.usernameCheck)
        assertFalse(vm.uiState.value.isSaveBlocked)
    }

    @Test
    fun `onSave 409 for username despite an earlier Available check marks it Taken and keeps the form`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        coEvery { userRepository.checkUsernameAvailability("bob") } returns
            ApiResult.Success(UsernameAvailabilityResponse(available = true, normalized = "bob"))
        coEvery { userRepository.updateUser(any()) } returns ApiResult.Error("Username is already taken")

        vm.onUsernameChanged("bob")
        advanceUntilIdle()
        assertEquals(UsernameCheckState.Available, vm.uiState.value.usernameCheck)

        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(UsernameCheckState.Taken, state.usernameCheck)
        assertEquals("bob", state.username)
        assertEquals("Username is already taken", state.fieldErrors[PersonalInfoField.USERNAME])
    }

    @Test
    fun `onSave 403 for a permanent field locks it locally and reloads eligibility`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { userRepository.updateUser(any()) } returns
            ApiResult.Error("Full name can only be changed once", code = "FULL_NAME_ALREADY_CHANGED")
        val refreshedUser = user().copy(canChangeFullName = false)
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(refreshedUser)

        vm.onFullNameChanged("New Alice")
        vm.onSave()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(false, state.canChange[PersonalInfoField.FULL_NAME])
        coVerify(exactly = 2) { userRepository.getCurrentUser() }
    }
}
