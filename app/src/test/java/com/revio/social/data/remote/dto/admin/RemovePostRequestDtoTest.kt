package com.revio.social.data.remote.dto.admin

import com.revio.social.data.model.ModerationReason
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the wire contract for admin post removal: each of the 13 [ModerationReason] values must
 * serialize to exactly the enum name the server expects
 * (revio-server's features/moderation/ModerationReason.kt — kept in sync by hand, there's no
 * shared module between the two repos). A renamed, reordered, or added/removed entry on either
 * side without updating the other would otherwise only surface as a runtime 400 from the server,
 * invisible until an admin happened to pick that exact reason.
 */
class RemovePostRequestDtoTest {

    private val json = Json

    // The server's own declared names, in the server's own declared order — a literal list so a
    // rename/reorder on either side breaks this test loudly instead of silently.
    private val expectedServerNames = listOf(
        "NO_CAR_CONTENT",
        "SEXUAL_CONTENT",
        "VIOLENT_CONTENT",
        "HATE_SPEECH",
        "HARASSMENT",
        "SPAM_OR_MISLEADING",
        "UNAUTHORIZED_ADVERTISING",
        "ILLEGAL_ACTIVITY",
        "PERSONAL_INFORMATION",
        "COPYRIGHT_INFRINGEMENT",
        "FAKE_OR_STOLEN_CONTENT",
        "LOW_QUALITY",
        "OTHER",
    )

    @Test
    fun `ModerationReason has exactly the 13 values the server expects, in order`() {
        assertEquals(expectedServerNames, ModerationReason.entries.map { it.name })
    }

    @Test
    fun `RemovePostRequestDto serializes reason to the exact server-expected enum name for all 13 reasons`() {
        ModerationReason.entries.forEachIndexed { index, reason ->
            val dto = RemovePostRequestDto(reason = reason, reasonDetails = null)
            val encoded = json.encodeToString(dto)

            assertTrue(
                "reason=$reason: expected wire name \"${expectedServerNames[index]}\" in $encoded",
                encoded.contains("\"reason\":\"${expectedServerNames[index]}\""),
            )
        }
    }
}
