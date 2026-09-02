package com.revio.social.data.remote.dto.activity

import com.revio.social.features.activity.model.ActivityItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ActivityDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `LIKE item without actorCount in the payload defaults to 1`() {
        val dto = json.decodeFromString<ActivityItemDto>(
            """
            {
                "type": "LIKE",
                "id": "like:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1000",
                "actorUserId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "actorUsername": "tommy82",
                "postId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "createdAt": "2026-08-25T10:00:00Z"
            }
            """.trimIndent(),
        )

        assertEquals(1, dto.actorCount)
        val domain = dto.toDomain() as ActivityItem.LikeItem
        assertEquals(1, domain.actorCount)
    }

    @Test
    fun `LIKE item propagates a server-provided actorCount through toDomain`() {
        val dto = json.decodeFromString<ActivityItemDto>(
            """
            {
                "type": "LIKE",
                "id": "like:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1000",
                "actorUserId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "actorUsername": "tommy82",
                "postId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "createdAt": "2026-08-25T10:00:00Z",
                "actorCount": 4
            }
            """.trimIndent(),
        )

        assertEquals(4, dto.actorCount)
        val domain = dto.toDomain() as ActivityItem.LikeItem
        assertEquals(4, domain.actorCount)
    }

    @Test
    fun `COMMENT item without actorCount in the payload defaults to 1`() {
        val dto = json.decodeFromString<ActivityItemDto>(
            """
            {
                "type": "COMMENT",
                "id": "comment:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1000",
                "actorUserId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "actorUsername": "charlotte_khan",
                "postId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "commentText": "Incredible spec",
                "createdAt": "2026-08-25T10:00:00Z"
            }
            """.trimIndent(),
        )

        assertEquals(1, dto.actorCount)
        val domain = dto.toDomain() as ActivityItem.CommentItem
        assertEquals(1, domain.actorCount)
    }

    @Test
    fun `COMMENT item propagates a server-provided actorCount through toDomain`() {
        val dto = ActivityItemDto(
            type = "COMMENT",
            id = "comment:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1000",
            actorUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            actorUsername = "charlotte_khan",
            postId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            commentText = null,
            createdAt = Instant.parse("2026-08-25T10:00:00Z"),
            actorCount = 3,
        )

        val domain = dto.toDomain() as ActivityItem.CommentItem
        assertEquals(3, domain.actorCount)
        // Server omits commentText for an aggregated (actorCount > 1) row; toDomain must not crash.
        assertEquals("", domain.commentText)
    }
}
