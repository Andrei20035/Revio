package com.revio.app.data.repository

import com.revio.app.core.network.ApiResult
import com.revio.app.data.local.preferences.UserPreferences
import com.revio.app.data.model.AuthProvider
import com.revio.app.data.remote.api.AuthApi
import com.revio.app.data.remote.dto.auth.AuthRequest
import com.revio.app.data.remote.dto.auth.AuthResponse
import com.revio.app.data.remote.dto.auth.OnboardingStep
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Repository-ul e thin: construiește AuthRequest și pasează prin safeApiCall.
 * Testăm exact aceste lucruri ca să prindem regresii dacă cineva uită
 * să trimită provider-ul corect, sau șterge prefs și pe error path
 * (deleteAccount).
 */
class AuthRepositoryImplTest {

    private lateinit var authApi: AuthApi
    private lateinit var userPreferences: UserPreferences
    private lateinit var repo: AuthRepositoryImpl

    @Before
    fun setup() {
        authApi = mockk()
        userPreferences = mockk(relaxed = true)
        repo = AuthRepositoryImpl(authApi, userPreferences)
    }

    @Test
    fun `login REGULAR trimite email si password si NU trimite googleIdToken`() = runTest {
        val captured = slot<AuthRequest>()
        coEvery { authApi.login(capture(captured)) } returns Response.success(
            AuthResponse(accessToken = "jwt", onboardingStep = OnboardingStep.COMPLETED)
        )

        val result = repo.login(
            email = "a@b.com",
            password = "secret",
            googleIdToken = null,
            provider = AuthProvider.REGULAR
        )

        assertTrue(result is ApiResult.Success)
        with(captured.captured) {
            assertEquals("a@b.com", email)
            assertEquals("secret", password)
            assertNull(googleIdToken)
            assertEquals(AuthProvider.REGULAR, provider)
        }
    }

    @Test
    fun `login GOOGLE trimite doar googleIdToken si provider GOOGLE`() = runTest {
        val captured = slot<AuthRequest>()
        coEvery { authApi.login(capture(captured)) } returns Response.success(
            AuthResponse(accessToken = "jwt-g", onboardingStep = OnboardingStep.PROFILE_REQUIRED)
        )

        repo.login(
            email = null,
            password = null,
            googleIdToken = "google-id-token",
            provider = AuthProvider.GOOGLE
        )

        with(captured.captured) {
            assertNull(email)
            assertNull(password)
            assertEquals("google-id-token", googleIdToken)
            assertEquals(AuthProvider.GOOGLE, provider)
        }
    }

    @Test
    fun `register propaga mesajul de eroare al serverului prin safeApiCall`() = runTest {
        val errorBody = """{"error":"Email already exists"}"""
            .toResponseBody("application/json".toMediaType())
        coEvery { authApi.register(any()) } returns Response.error(409, errorBody)

        val result = repo.register("a@b.com", "password123", null, AuthProvider.REGULAR)

        assertTrue(result is ApiResult.Error)
        assertEquals("Email already exists", (result as ApiResult.Error).message)
    }

    @Test
    fun `logout sterge auth data din UserPreferences`() = runTest {
        repo.logout()

        coVerify(exactly = 1) { userPreferences.clearAuthData() }
    }

    @Test
    fun `deleteAccount sterge prefs DOAR daca apelul a reusit`() = runTest {
        coEvery { authApi.deleteAccount() } returns Response.success(Unit)

        val result = repo.deleteAccount()

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { userPreferences.clearAuthData() }
    }

    @Test
    fun `deleteAccount NU sterge prefs daca serverul intoarce eroare`() = runTest {
        val errorBody = """{"error":"forbidden"}"""
            .toResponseBody("application/json".toMediaType())
        coEvery { authApi.deleteAccount() } returns Response.error(403, errorBody)

        val result = repo.deleteAccount()

        assertTrue(result is ApiResult.Error)
        coVerify(exactly = 0) { userPreferences.clearAuthData() }
    }
}
