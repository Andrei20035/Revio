package com.revio.social.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RevioMessagingServiceTest {

    @Test
    fun `blank body resolves to a title-only notification`() {
        val resolved = resolveNotification(
            payload = mapOf("title" to "Andrei liked your spot", "body" to "", "category" to "LIKES"),
            category = "LIKES",
            fallbackTitle = "Revio",
        )

        assertEquals("Andrei liked your spot", resolved.title)
        assertNull(resolved.body)
        assertEquals("likes", resolved.channelId)
    }

    @Test
    fun `missing body key resolves to a title-only notification`() {
        val resolved = resolveNotification(
            payload = mapOf("title" to "Andrei commented on your spot", "category" to "COMMENTS"),
            category = "COMMENTS",
            fallbackTitle = "Revio",
        )

        assertEquals("Andrei commented on your spot", resolved.title)
        assertNull(resolved.body)
        assertEquals("comments", resolved.channelId)
    }

    @Test
    fun `non-blank body is preserved`() {
        val resolved = resolveNotification(
            payload = mapOf(
                "title" to "Your spot is getting noticed",
                "body" to "4 new likes since you posted.",
                "category" to "LIKES",
            ),
            category = "LIKES",
            fallbackTitle = "Revio",
        )

        assertEquals("Your spot is getting noticed", resolved.title)
        assertEquals("4 new likes since you posted.", resolved.body)
    }

    @Test
    fun `blank title falls back and notification is still resolved`() {
        val resolved = resolveNotification(
            payload = mapOf("title" to "", "body" to ""),
            category = null,
            fallbackTitle = "Revio",
        )

        assertEquals("Revio", resolved.title)
        assertNull(resolved.body)
        assertNotNull(resolved.notificationId)
    }

    @Test
    fun `unknown category falls back to account channel`() {
        val resolved = resolveNotification(
            payload = mapOf("title" to "Welcome back"),
            category = "SOMETHING_ELSE",
            fallbackTitle = "Revio",
        )

        assertEquals("account", resolved.channelId)
    }
}
