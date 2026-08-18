package com.revio.social.core.navigation

import android.net.Uri
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ScreenTest {

    @Test
    fun `ChallengeDetail createRoute produce ruta asteptata`() {
        val challengeId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

        val route = Screen.ChallengeDetail.createRoute(challengeId)

        assertEquals("challenge/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", route)
    }

    @Test
    fun `MyChallenges route este statica`() {
        assertEquals("my_challenges", Screen.MyChallenges.route)
    }

    @Test
    fun `AdminModeration route este statica`() {
        assertEquals("admin/moderation", Screen.AdminModeration.route)
    }

    @Test
    fun `AdminChallenges route este statica`() {
        assertEquals("admin/challenges", Screen.AdminChallenges.route)
    }

    // ---- ProfileCustomization.createRoute: waitlist suggestedUsername encoding round-trip ----

    /**
     * android.net.Uri isn't available in a plain JVM unit test (no Robolectric here), so
     * Uri.encode is stubbed with the standard query-component percent-encoding it wraps
     * (URLEncoder with "+" swapped for the literal "%20" space encoding Uri.encode actually
     * produces) — real enough to catch createRoute building a broken query string, without
     * pulling in an Android runtime just for this one call.
     */
    @Before
    fun stubUriEncode() {
        mockkStatic(Uri::class)
        every { Uri.encode(any<String>()) } answers {
            URLEncoder.encode(firstArg<String>(), "UTF-8").replace("+", "%20")
        }
    }

    @After
    fun unstubUriEncode() {
        unmockkStatic(Uri::class)
    }

    private fun decodeSuggestedUsernameParam(route: String): String {
        val encoded = Regex("suggestedUsername=([^&]*)").find(route)!!.groupValues[1]
        return URLDecoder.decode(encoded.replace("%20", "+"), "UTF-8")
    }

    @Test
    fun `ProfileCustomization createRoute with an ampersand in the username round-trips identically`() {
        val original = "cool&name"

        val route = Screen.ProfileCustomization.createRoute(
            suggestedUsername = original,
            suggestedUsernameStatus = "AVAILABLE",
        )

        assertEquals(original, decodeSuggestedUsernameParam(route))
    }

    @Test
    fun `ProfileCustomization createRoute with a question mark in the username round-trips identically`() {
        val original = "who?dis"

        val route = Screen.ProfileCustomization.createRoute(
            suggestedUsername = original,
            suggestedUsernameStatus = "AVAILABLE",
        )

        assertEquals(original, decodeSuggestedUsernameParam(route))
    }

    @Test
    fun `ProfileCustomization createRoute with spaces in the username round-trips identically`() {
        val original = "cool guy 42"

        val route = Screen.ProfileCustomization.createRoute(
            suggestedUsername = original,
            suggestedUsernameStatus = "AVAILABLE",
        )

        assertEquals(original, decodeSuggestedUsernameParam(route))
    }

    @Test
    fun `ProfileCustomization createRoute with diacritics in the username round-trips identically`() {
        val original = "andrei_ăâîșț"

        val route = Screen.ProfileCustomization.createRoute(
            suggestedUsername = original,
            suggestedUsernameStatus = "AVAILABLE",
        )

        assertEquals(original, decodeSuggestedUsernameParam(route))
    }

    @Test
    fun `ProfileCustomization createRoute with all special characters combined round-trips identically`() {
        val original = "a & weird? name ăâî"

        val route = Screen.ProfileCustomization.createRoute(
            suggestedUsername = original,
            suggestedUsernameStatus = "AVAILABLE",
        )

        assertEquals(original, decodeSuggestedUsernameParam(route))
        // The route's own query structure must survive too — exactly one status param, still parseable.
        assertEquals(1, Regex("suggestedUsernameStatus=").findAll(route).count())
        assertEquals("AVAILABLE", Regex("suggestedUsernameStatus=([^&]*)").find(route)!!.groupValues[1])
    }
}
