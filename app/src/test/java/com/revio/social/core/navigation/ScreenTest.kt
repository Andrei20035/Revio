package com.revio.social.core.navigation

import java.util.UUID
import org.junit.Assert.assertEquals
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
}
