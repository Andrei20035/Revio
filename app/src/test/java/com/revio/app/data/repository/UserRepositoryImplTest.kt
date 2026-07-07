package com.revio.app.data.repository

import com.revio.app.core.network.ApiResult
import com.revio.app.data.model.User
import com.revio.app.data.remote.api.UserApi
import com.revio.app.data.remote.dto.user.UpdateUserRequest
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class UserRepositoryImplTest {

    private val api: UserApi = mockk()
    private lateinit var repository: UserRepositoryImpl

    private val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val jsonMedia = "application/json".toMediaType()

    private fun user() = User(
        id = userId,
        fullName = "Test User",
        username = "testuser",
        country = "Romania",
    )

    @Before
    fun setUp() {
        repository = UserRepositoryImpl(api)
    }

    @Test
    fun `getUserById success returneaza ApiResult Success cu datele utilizatorului`() = runTest {
        coEvery { api.getUserById(userId) } returns Response.success(user())

        val result = repository.getUserById(userId)

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(userId, data.id)
        assertEquals("testuser", data.username)
        assertEquals("Test User", data.fullName)
    }

    @Test
    fun `getUserById 404 returneaza ApiResult Error cu mesajul din body`() = runTest {
        val errorBody = """{"error":"User not found"}""".toResponseBody(jsonMedia)
        coEvery { api.getUserById(userId) } returns Response.error(404, errorBody)

        val result = repository.getUserById(userId)

        assertTrue(result is ApiResult.Error)
        assertEquals("User not found", (result as ApiResult.Error).message)
    }

    @Test
    fun `getUserById eroare de retea returneaza ApiResult Error cu prefixul Network error`() = runTest {
        coEvery { api.getUserById(userId) } throws IOException("Connection refused")

        val result = repository.getUserById(userId)

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).message.startsWith("Network error"))
    }

    @Test
    fun `updateUser success returneaza ApiResult Success cu datele utilizatorului`() = runTest {
        val request = UpdateUserRequest(fullName = "New Name")
        coEvery { api.updateUser(request) } returns Response.success(user().copy(fullName = "New Name"))

        val result = repository.updateUser(request)

        assertTrue(result is ApiResult.Success)
        assertEquals("New Name", (result as ApiResult.Success).data.fullName)
    }

    @Test
    fun `updateUser 409 returneaza ApiResult Error cu mesajul de username duplicat`() = runTest {
        val request = UpdateUserRequest(username = "taken")
        val errorBody = """{"error":"Username is already taken"}""".toResponseBody(jsonMedia)
        coEvery { api.updateUser(request) } returns Response.error(409, errorBody)

        val result = repository.updateUser(request)

        assertTrue(result is ApiResult.Error)
        assertEquals("Username is already taken", (result as ApiResult.Error).message)
    }

    @Test
    fun `updateUser 403 returneaza ApiResult Error cu code din corpul imbricat`() = runTest {
        val request = UpdateUserRequest(fullName = "Second Change")
        val errorBody = """{"error":{"code":"FULL_NAME_ALREADY_CHANGED","message":"Full name can only be changed once"}}"""
            .toResponseBody(jsonMedia)
        coEvery { api.updateUser(request) } returns Response.error(403, errorBody)

        val result = repository.updateUser(request)

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals("Full name can only be changed once", error.message)
        assertEquals("FULL_NAME_ALREADY_CHANGED", error.code)
    }

    @Test
    fun `updateUser 400 returneaza ApiResult Error cu mesajul de format invalid`() = runTest {
        val request = UpdateUserRequest(username = "bad-name")
        val errorBody = """{"error":"Username may contain only lowercase letters, digits, dot, and underscore"}"""
            .toResponseBody(jsonMedia)
        coEvery { api.updateUser(request) } returns Response.error(400, errorBody)

        val result = repository.updateUser(request)

        assertTrue(result is ApiResult.Error)
        assertEquals(
            "Username may contain only lowercase letters, digits, dot, and underscore",
            (result as ApiResult.Error).message,
        )
    }
}
