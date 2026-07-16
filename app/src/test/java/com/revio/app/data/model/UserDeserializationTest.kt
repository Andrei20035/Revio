package com.revio.app.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class UserDeserializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val baseJson = """
        {
            "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "fullName": "Test User",
            "username": "test_user",
            "country": "Romania"
        }
    """.trimIndent()

    private val jsonWithStreak = """
        {
            "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "fullName": "Test User",
            "username": "test_user",
            "country": "Romania",
            "streakDays": 7
        }
    """.trimIndent()

    @Test
    fun `JSON fara streakDays - deserializare reusita, streakDays default zero`() {
        val user = json.decodeFromString<User>(baseJson)
        assertEquals(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), user.id)
        assertEquals("Test User", user.fullName)
        assertEquals(0, user.streakDays)
    }

    @Test
    fun `JSON cu streakDays pozitiv - deserializare corecta`() {
        val user = json.decodeFromString<User>(jsonWithStreak)
        assertEquals(7, user.streakDays)
    }

    @Test
    fun `JSON cu streakDays zero - deserializare corecta`() {
        val jsonZero = """
            {
                "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "fullName": "Test User",
                "username": "test_user",
                "country": "Romania",
                "streakDays": 0
            }
        """.trimIndent()
        val user = json.decodeFromString<User>(jsonZero)
        assertEquals(0, user.streakDays)
    }

    @Test
    fun `JSON cu campuri extra necunoscute - nu crapa, ignora campurile`() {
        val jsonWithExtra = """
            {
                "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "fullName": "Test User",
                "username": "test_user",
                "country": "Romania",
                "streakDays": 3,
                "unknownField": "ignored"
            }
        """.trimIndent()
        val user = json.decodeFromString<User>(jsonWithExtra)
        assertEquals(3, user.streakDays)
    }

    @Test
    fun `JSON fara streakDays - profilePicturePath null by default`() {
        val user = json.decodeFromString<User>(baseJson)
        assertNull(user.profilePicturePath)
    }

    @Test
    fun `JSON fara canChange - toate default true, next-change timestamps null`() {
        val user = json.decodeFromString<User>(baseJson)
        assertTrue(user.canChangeFullName)
        assertTrue(user.canChangeCountry)
        assertTrue(user.canChangeBirthDate)
        assertTrue(user.canChangeUsername)
        assertTrue(user.canChangePhoneNumber)
        assertNull(user.nextUsernameChangeAt)
        assertNull(user.nextPhoneNumberChangeAt)
    }
}
