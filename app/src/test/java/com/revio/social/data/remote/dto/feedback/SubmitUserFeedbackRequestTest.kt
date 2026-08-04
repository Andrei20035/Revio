package com.revio.social.data.remote.dto.feedback

import com.revio.social.data.model.ConfusionReason
import com.revio.social.data.model.FeedbackArea
import com.revio.social.data.model.FeedbackCategory
import com.revio.social.data.model.FeedbackPriority
import com.revio.social.data.model.FeedbackSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class SubmitUserFeedbackRequestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `encodes all fields with the keys the server expects`() {
        val clientFeedbackId = UUID.randomUUID()
        val request = SubmitUserFeedbackRequest(
            category = FeedbackCategory.FEATURE_IDEA,
            area = FeedbackArea.FEED,
            message = "Add dark mode toggle",
            secondaryMessage = "It would help at night",
            quickReason = ConfusionReason.OTHER,
            priority = FeedbackPriority.IMPORTANT,
            rating = 4,
            keepMessage = "Keep the feed",
            improveMessage = "Improve search",
            source = FeedbackSource.SETTINGS_FEEDBACK,
            originScreen = "settings",
            includeDiagnostics = true,
            appVersion = "1.0",
            androidVersion = "14",
            deviceModel = "Pixel 9 Pro",
            connectionType = "wifi",
            lastErrorCode = "network_unavailable",
            clientFeedbackId = clientFeedbackId,
            clientSubmittedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        val element = json.encodeToJsonElement(SubmitUserFeedbackRequest.serializer(), request).jsonObject

        assertEquals("FEATURE_IDEA", element["category"]?.jsonPrimitive?.content)
        assertEquals("FEED", element["area"]?.jsonPrimitive?.content)
        assertEquals("Add dark mode toggle", element["message"]?.jsonPrimitive?.content)
        assertEquals("It would help at night", element["secondaryMessage"]?.jsonPrimitive?.content)
        assertEquals("OTHER", element["quickReason"]?.jsonPrimitive?.content)
        assertEquals("IMPORTANT", element["priority"]?.jsonPrimitive?.content)
        assertEquals(4, element["rating"]?.jsonPrimitive?.content?.toInt())
        assertEquals("Keep the feed", element["keepMessage"]?.jsonPrimitive?.content)
        assertEquals("Improve search", element["improveMessage"]?.jsonPrimitive?.content)
        assertEquals("SETTINGS_FEEDBACK", element["source"]?.jsonPrimitive?.content)
        assertEquals("settings", element["originScreen"]?.jsonPrimitive?.content)
        assertEquals(true, element["includeDiagnostics"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals("1.0", element["appVersion"]?.jsonPrimitive?.content)
        assertEquals("14", element["androidVersion"]?.jsonPrimitive?.content)
        assertEquals("Pixel 9 Pro", element["deviceModel"]?.jsonPrimitive?.content)
        assertEquals("wifi", element["connectionType"]?.jsonPrimitive?.content)
        assertEquals("network_unavailable", element["lastErrorCode"]?.jsonPrimitive?.content)
        assertEquals(clientFeedbackId.toString(), element["clientFeedbackId"]?.jsonPrimitive?.content)
        assertEquals("2026-01-01T00:00:00Z", element["clientSubmittedAt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `omits optional fields from the payload when null`() {
        val request = SubmitUserFeedbackRequest(
            category = FeedbackCategory.GENERAL,
            source = FeedbackSource.SETTINGS_FEEDBACK,
            clientFeedbackId = UUID.randomUUID(),
        )

        val element = json.encodeToJsonElement(SubmitUserFeedbackRequest.serializer(), request).jsonObject

        assertEquals("GENERAL", element["category"]?.jsonPrimitive?.content)
        assertEquals(false, element.containsKey("includeDiagnostics"))
        assertEquals(false, element.containsKey("message"))
        assertEquals(false, element.containsKey("area"))
        assertEquals(false, element.containsKey("clientSubmittedAt"))
    }
}
