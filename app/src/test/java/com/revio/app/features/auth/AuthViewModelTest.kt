package com.revio.app.features.auth

import app.cash.turbine.test
import com.revio.app.MainDispatcherRule
import com.revio.app.core.network.ApiResult
import com.revio.app.data.local.preferences.UserPreferences
import com.revio.app.data.model.AuthProvider
import com.revio.app.data.model.User
import com.revio.app.data.remote.dto.auth.AuthResponse
import com.revio.app.data.remote.dto.auth.OnboardingStep
import com.revio.app.data.repository.AuthRepository
import com.revio.app.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

/**
 * Aici testăm tot flow-ul real de auth la nivel de ViewModel.
 * Repository-ul și UserPreferences sunt mock-uite — nu atingem rețea sau DataStore.
 */
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var userPreferences: UserPreferences
    private lateinit var vm: AuthViewModel
    private val existingUserId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Before
    fun setup() {
        authRepository = mockk()
        userRepository = mockk()
        userPreferences = mockk(relaxed = true)
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(existingUser())
        vm = AuthViewModel(userPreferences, authRepository, userRepository)
    }

    private fun existingUser() = User(
        id = existingUserId,
        fullName = "Existing User",
        phoneNumber = "",
        birthDate = LocalDate.of(2000, 1, 1),
        username = "existing_user",
        country = "Romania"
    )

    private fun jwtWithUserId(userId: UUID): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString("""{"userId":"$userId"}""".toByteArray())
        return "$header.$payload."
    }

    private fun jwtWithCredentialIdOnly(credentialId: UUID): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString("""{"credentialId":"$credentialId"}""".toByteArray())
        return "$header.$payload."
    }

    // ----------------------------------------------------------------------
    // LOGIN cu email + password (REGULAR)
    // ----------------------------------------------------------------------

    /**
     * Happy path: login reușit cu onboarding complet → JWT salvat și user duăs la Feed.
     */
    @Test
    fun `login regular success - salveaza JWT, naviga la Feed, foloseste provider REGULAR`() = runTest {
        coEvery {
            authRepository.login(
                email = "a@b.com",
                password = "secret",
                googleIdToken = null,
                provider = AuthProvider.REGULAR
            )
        } returns ApiResult.Success(AuthResponse("jwt-feed", OnboardingStep.COMPLETED))

        vm.updateEmail("  a@b.com  ")   // ViewModel-ul face trim()
        vm.updatePassword("secret")
        vm.submitEmailAuth()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(AuthNavigationEvent.ToFeed, state.navigationEvent)
        coVerify(exactly = 1) { userPreferences.saveJwtToken("jwt-feed") }
        coVerify(exactly = 1) { userPreferences.saveUserId(existingUserId) }
    }

    /**
     * Server-ul ne spune că profilul nu e gata → mergem la profile customization,
     * NU la Feed. Asta e singurul gating între login reușit și ecranul greșit.
     */
    @Test
    fun `login regular cu PROFILE_REQUIRED naviga la profile customization`() = runTest {
        coEvery { authRepository.login(any(), any(), any(), AuthProvider.REGULAR) } returns
                ApiResult.Success(AuthResponse("jwt-pc", OnboardingStep.PROFILE_REQUIRED))

        vm.updateEmail("a@b.com"); vm.updatePassword("secret")
        vm.submitEmailAuth()

        assertEquals(
            AuthNavigationEvent.ToProfileCustomization,
            vm.uiState.value.navigationEvent
        )
        coVerify(exactly = 1) { userPreferences.saveJwtToken("jwt-pc") }
    }

    @Test
    fun `login regular cu PROFILE_REQUIRED ramane la profile customization chiar daca JWT are userId`() = runTest {
        val jwt = jwtWithUserId(existingUserId)
        coEvery { authRepository.login(any(), any(), any(), AuthProvider.REGULAR) } returns
                ApiResult.Success(AuthResponse(jwt, OnboardingStep.PROFILE_REQUIRED))

        vm.updateEmail("a@b.com"); vm.updatePassword("secret")
        vm.submitEmailAuth()

        assertEquals(AuthNavigationEvent.ToProfileCustomization, vm.uiState.value.navigationEvent)
        coVerify(exactly = 1) { userPreferences.saveJwtToken(jwt) }
        coVerify(exactly = 0) { userPreferences.saveUserId(any()) }
    }

    @Test
    fun `login regular cu COMPLETED dar doar credentialId in JWT verifica profilul inainte de Feed`() = runTest {
        val jwt = jwtWithCredentialIdOnly(UUID.fromString("22222222-2222-2222-2222-222222222222"))
        coEvery { authRepository.login(any(), any(), any(), AuthProvider.REGULAR) } returns
                ApiResult.Success(AuthResponse(jwt, OnboardingStep.COMPLETED))
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Error("Profile not found")

        vm.updateEmail("a@b.com"); vm.updatePassword("secret")
        vm.submitEmailAuth()

        assertEquals(AuthNavigationEvent.ToProfileCustomization, vm.uiState.value.navigationEvent)
        coVerify(exactly = 1) { userRepository.getCurrentUser() }
        coVerify(exactly = 0) { userPreferences.saveUserId(any()) }
    }

    @Test
    fun `login regular cu COMPLETED si userId in JWT dar fara user pe server naviga la profile customization`() = runTest {
        val jwt = jwtWithUserId(existingUserId)
        coEvery { authRepository.login(any(), any(), any(), AuthProvider.REGULAR) } returns
                ApiResult.Success(AuthResponse(jwt, OnboardingStep.COMPLETED))
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Error("Profile not found")

        vm.updateEmail("a@b.com"); vm.updatePassword("secret")
        vm.submitEmailAuth()

        assertEquals(AuthNavigationEvent.ToProfileCustomization, vm.uiState.value.navigationEvent)
        coVerify(exactly = 1) { userRepository.getCurrentUser() }
        coVerify(exactly = 0) { userPreferences.saveUserId(any()) }
    }

    /**
     * Email gol pe login → eroare locală, NU lovim repo-ul, NU salvăm JWT.
     * (Login-ul nu validează formatul, doar blank — testul reflectă comportamentul real.)
     */
    @Test
    fun `login regular cu email gol - eroare locala, fara apel la repo`() = runTest {
        vm.updateEmail("")
        vm.updatePassword("secret")
        vm.submitEmailAuth()

        val state = vm.uiState.value
        assertEquals("Email cannot be empty", state.errorMessage)
        assertFalse(state.isLoading)
        coVerify(exactly = 0) { authRepository.login(any(), any(), any(), any()) }
        coVerify(exactly = 0) { userPreferences.saveJwtToken(any()) }
    }

    /**
     * Wrong password = server-ul întoarce ApiResult.Error.
     * Trebuie să apară mesajul, isLoading să fie false, navigationEvent să fie null,
     * și NU se salvează niciun JWT.
     */
    @Test
    fun `login regular cu parola gresita - mesaj eroare server, fara JWT salvat`() = runTest {
        coEvery { authRepository.login(any(), any(), any(), AuthProvider.REGULAR) } returns
                ApiResult.Error("Invalid credentials")

        vm.updateEmail("a@b.com"); vm.updatePassword("wrong")
        vm.submitEmailAuth()

        val state = vm.uiState.value
        assertEquals("Invalid credentials", state.errorMessage)
        assertFalse(state.isLoading)
        assertNull(state.navigationEvent)
        coVerify(exactly = 0) { userPreferences.saveJwtToken(any()) }
    }

    // ----------------------------------------------------------------------
    // REGISTER
    // ----------------------------------------------------------------------

    @Test
    fun `register success - salveaza JWT si naviga la profile customization`() = runTest {
        coEvery { authRepository.register(any(), any(), any(), AuthProvider.REGULAR) } returns
                ApiResult.Success(AuthResponse("jwt-new", OnboardingStep.PROFILE_REQUIRED))

        vm.toggleLoginMode()                         // intrăm în mod register
        vm.updateEmail("new@user.com")
        vm.updatePassword("Password!123")
        vm.updateConfirmPassword("Password!123")
        vm.submitEmailAuth()

        assertEquals(
            AuthNavigationEvent.ToProfileCustomization,
            vm.uiState.value.navigationEvent
        )
        coVerify(exactly = 1) { userPreferences.saveJwtToken("jwt-new") }
    }

    @Test
    fun `register cu parole diferite - eroare locala, fara apel la repo`() = runTest {
        vm.toggleLoginMode()
        vm.updateEmail("a@b.com")
        vm.updatePassword("password123")
        vm.updateConfirmPassword("password124")
        vm.submitEmailAuth()

        assertEquals("Passwords do not match", vm.uiState.value.errorMessage)
        coVerify(exactly = 0) { authRepository.register(any(), any(), any(), any()) }
    }

    @Test
    fun `register cu email invalid - eroare locala`() = runTest {
        vm.toggleLoginMode()
        vm.updateEmail("not-an-email")
        vm.updatePassword("password123")
        vm.updateConfirmPassword("password123")
        vm.submitEmailAuth()

        assertEquals("Invalid email format", vm.uiState.value.errorMessage)
        coVerify(exactly = 0) { authRepository.register(any(), any(), any(), any()) }
    }

    @Test
    fun `register cu parola sub 8 caractere - eroare locala`() = runTest {
        vm.toggleLoginMode()
        vm.updateEmail("a@b.com")
        vm.updatePassword("short")
        vm.updateConfirmPassword("short")
        vm.submitEmailAuth()

        assertTrue(
            vm.uiState.value.errorMessage?.contains("8+ characters") == true
        )
        coVerify(exactly = 0) { authRepository.register(any(), any(), any(), any()) }
    }

    // ----------------------------------------------------------------------
    // GOOGLE LOGIN
    // ----------------------------------------------------------------------

    @Test
    fun `google login success - foloseste provider GOOGLE, salveaza JWT, naviga`() = runTest {
        coEvery {
            authRepository.login(
                email = null,
                password = null,
                googleIdToken = "real-id-token",
                provider = AuthProvider.GOOGLE
            )
        } returns ApiResult.Success(AuthResponse("jwt-g", OnboardingStep.COMPLETED))

        vm.loginWithGoogle("real-id-token")

        assertEquals(AuthNavigationEvent.ToFeed, vm.uiState.value.navigationEvent)
        coVerify(exactly = 1) { userPreferences.saveJwtToken("jwt-g") }
        coVerify(exactly = 1) { userRepository.getCurrentUser() }
        coVerify(exactly = 1) { userPreferences.saveUserId(existingUserId) }
    }

    @Test
    fun `google login cu COMPLETED dar fara profil curent naviga la profile customization`() = runTest {
        coEvery {
            authRepository.login(
                email = null,
                password = null,
                googleIdToken = "real-id-token",
                provider = AuthProvider.GOOGLE
            )
        } returns ApiResult.Success(AuthResponse("jwt-g", OnboardingStep.COMPLETED))
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Error("Profile not found")

        vm.loginWithGoogle("real-id-token")

        assertEquals(AuthNavigationEvent.ToProfileCustomization, vm.uiState.value.navigationEvent)
        coVerify(exactly = 1) { userPreferences.saveJwtToken("jwt-g") }
        coVerify(exactly = 0) { userPreferences.saveUserId(any()) }
    }

    /**
     * Token null = userul a anulat picker-ul Google sau Play Services au eșuat.
     * Nu trebuie să facem niciun apel la backend.
     */
    @Test
    fun `google login cu idToken null - eroare, fara apel la repo`() = runTest {
        vm.loginWithGoogle(null)

        assertEquals(
            "Google sign-in cancelled or failed",
            vm.uiState.value.errorMessage
        )
        coVerify(exactly = 0) { authRepository.login(any(), any(), any(), any()) }
        coVerify(exactly = 0) { userPreferences.saveJwtToken(any()) }
    }

    @Test
    fun `google login cu idToken blank - eroare, fara apel la repo`() = runTest {
        vm.loginWithGoogle("   ")

        assertEquals(
            "Google sign-in cancelled or failed",
            vm.uiState.value.errorMessage
        )
        coVerify(exactly = 0) { authRepository.login(any(), any(), any(), any()) }
    }

    /**
     * Regression test pentru "provider stale on GOOGLE":
     * după un login Google reușit, dacă userul revine și face login regular,
     * cel de-al doilea apel TREBUIE să folosească provider REGULAR și să NU
     * trimită googleIdToken-ul anterior. Dacă cineva mută provider-ul în state
     * și uită să-l reseteze, testul ăsta cade.
     */
    @Test
    fun `dupa google login, login regular foloseste REGULAR si NU reutilizeaza googleIdToken`() = runTest {
        coEvery { authRepository.login(any(), any(), any(), any()) } returns
                ApiResult.Success(AuthResponse("jwt", OnboardingStep.COMPLETED))

        // 1. Login Google
        vm.loginWithGoogle("g-token")
        vm.consumeNavigationEvent()

        // 2. Login regular
        vm.updateEmail("a@b.com"); vm.updatePassword("secret")
        vm.submitEmailAuth()

        // Cel de-al doilea apel trebuie să fie REGULAR cu email/password,
        // FĂRĂ googleIdToken
        coVerify(exactly = 1) {
            authRepository.login(
                email = "a@b.com",
                password = "secret",
                googleIdToken = null,
                provider = AuthProvider.REGULAR
            )
        }
        // Iar primul apel a fost cel de Google
        coVerify(exactly = 1) {
            authRepository.login(
                email = null,
                password = null,
                googleIdToken = "g-token",
                provider = AuthProvider.GOOGLE
            )
        }
    }

    // ----------------------------------------------------------------------
    // ERROR / SNACKBAR / NAVIGATION HANDLING
    // ----------------------------------------------------------------------

    @Test
    fun `onErrorShown sterge errorMessage dar pastreaza errorId`() = runTest {
        coEvery { authRepository.login(any(), any(), any(), any()) } returns
                ApiResult.Error("server boom")

        vm.updateEmail("a@b.com"); vm.updatePassword("p")
        vm.submitEmailAuth()
        val errorIdAfterFirst = vm.uiState.value.errorId
        assertEquals("server boom", vm.uiState.value.errorMessage)

        vm.onErrorShown()

        assertNull(vm.uiState.value.errorMessage)
        assertEquals(errorIdAfterFirst, vm.uiState.value.errorId) // nu se schimbă
    }

    /**
     * Bugul real: dacă errorId NU se schimbă pe a doua eroare identică,
     * snackbar-ul nu se mai re-afișează în Compose pentru că nu detectează
     * o emisie nouă. Testăm că errorId crește strict de fiecare dată.
     */
    @Test
    fun `errorId creste la fiecare eroare - snackbar poate fi re-afisat`() = runTest {
        coEvery { authRepository.login(any(), any(), any(), any()) } returns
                ApiResult.Error("Invalid credentials")

        vm.updateEmail("a@b.com"); vm.updatePassword("p")
        vm.submitEmailAuth()
        val firstId = vm.uiState.value.errorId

        vm.onErrorShown()                  // simulăm că snackbar-ul s-a închis
        vm.submitEmailAuth()               // aceeași eroare a doua oară
        val secondId = vm.uiState.value.errorId

        assertTrue(
            "errorId trebuie să crească strict ca să retrigger-uie snackbar",
            secondId > firstId
        )
        assertEquals("Invalid credentials", vm.uiState.value.errorMessage)
    }

    @Test
    fun `consumeNavigationEvent sterge navigation event-ul`() = runTest {
        coEvery { authRepository.login(any(), any(), any(), any()) } returns
                ApiResult.Success(AuthResponse("jwt", OnboardingStep.COMPLETED))

        vm.updateEmail("a@b.com"); vm.updatePassword("p")
        vm.submitEmailAuth()
        assertNotNull(vm.uiState.value.navigationEvent)

        vm.consumeNavigationEvent()

        assertNull(vm.uiState.value.navigationEvent)
    }

    /**
     * toggleLoginMode trebuie să curețe parolele și errorMessage,
     * ca utilizatorul să nu vadă o eroare veche în alt tab (login vs register).
     */
    @Test
    fun `toggleLoginMode reseteaza parolele si errorMessage`() = runTest {
        coEvery { authRepository.login(any(), any(), any(), any()) } returns
                ApiResult.Error("nope")
        vm.updateEmail("a@b.com"); vm.updatePassword("secret"); vm.updateConfirmPassword("secret")
        vm.submitEmailAuth()
        assertNotNull(vm.uiState.value.errorMessage)

        vm.toggleLoginMode()

        val state = vm.uiState.value
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertNull(state.errorMessage)
    }

    // ----------------------------------------------------------------------
    // StateFlow emisii (Turbine)
    // ----------------------------------------------------------------------

    /**
     * Verificăm că StateFlow-ul chiar emite când se schimbă state-ul.
     * Nu testăm fiecare tranziție de isLoading — doar că starea finală
     * după un login reușit conține JWT salvat și navigationEvent corect.
     */
    @Test
    fun `uiState emite cand inputurile se schimba si la final dupa login`() = runTest {
        coEvery { authRepository.login(any(), any(), any(), any()) } returns
                ApiResult.Success(AuthResponse("jwt", OnboardingStep.COMPLETED))

        vm.uiState.test {
            // emisia inițială
            assertEquals(AuthUiState(), awaitItem())

            vm.updateEmail("a@b.com")
            assertEquals("a@b.com", awaitItem().email)

            vm.updatePassword("secret")
            assertEquals("secret", awaitItem().password)

            vm.submitEmailAuth()
            // după ce flow-ul de login se termină, ne uităm doar la starea finală
            val finalState = expectMostRecentItem()
            assertEquals(AuthNavigationEvent.ToFeed, finalState.navigationEvent)
            assertFalse(finalState.isLoading)
            assertNull(finalState.errorMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
