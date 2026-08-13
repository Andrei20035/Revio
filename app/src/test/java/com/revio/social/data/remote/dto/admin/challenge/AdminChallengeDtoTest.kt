package com.revio.social.data.remote.dto.admin.challenge

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class AdminChallengeDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val challengeAdminJson = """
        {
            "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "title": "Weekend Golf Hunt",
            "description": "Find every Golf you can this weekend",
            "targetFamilyId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            "requiredPosts": 5,
            "rewardPoints": 300,
            "startsAt": "2026-08-07T00:00:00Z",
            "endsAt": "2026-08-09T00:00:00Z",
            "adminTimezone": "Europe/Bucharest",
            "status": "SCHEDULED",
            "createdBy": "cccccccc-cccc-cccc-cccc-cccccccccccc",
            "createdAt": "2026-08-01T00:00:00Z",
            "updatedAt": "2026-08-02T00:00:00Z",
            "publishedAt": "2026-08-02T00:00:00Z",
            "cancelledAt": null,
            "finalizedAt": null
        }
    """.trimIndent()

    @Test
    fun `ChallengeAdminDto se deserializeaza corect`() {
        val dto = json.decodeFromString<ChallengeAdminDto>(challengeAdminJson)

        assertEquals(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), dto.id)
        assertEquals("Weekend Golf Hunt", dto.title)
        assertEquals("Find every Golf you can this weekend", dto.description)
        assertEquals(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), dto.targetFamilyId)
        assertEquals(5, dto.requiredPosts)
        assertEquals(300, dto.rewardPoints)
        assertEquals(Instant.parse("2026-08-07T00:00:00Z"), dto.startsAt)
        assertEquals(Instant.parse("2026-08-09T00:00:00Z"), dto.endsAt)
        assertEquals("Europe/Bucharest", dto.adminTimezone)
        assertEquals("SCHEDULED", dto.status)
        assertEquals(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), dto.createdBy)
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), dto.createdAt)
        assertEquals(Instant.parse("2026-08-02T00:00:00Z"), dto.updatedAt)
        assertEquals(Instant.parse("2026-08-02T00:00:00Z"), dto.publishedAt)
        assertNull(dto.cancelledAt)
        assertNull(dto.finalizedAt)
    }

    @Test
    fun `ChallengeAdminDto fara finalizedAt in payload - se deserializeaza cu null (client vechi vs server nou)`() {
        val jsonWithoutFinalizedAt = """
            {
                "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "title": "Weekend Golf Hunt",
                "targetFamilyId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "requiredPosts": 5,
                "rewardPoints": 300,
                "startsAt": "2026-08-07T00:00:00Z",
                "endsAt": "2026-08-09T00:00:00Z",
                "adminTimezone": "Europe/Bucharest",
                "status": "DRAFT",
                "createdAt": "2026-08-01T00:00:00Z",
                "updatedAt": "2026-08-01T00:00:00Z"
            }
        """.trimIndent()
        val dto = json.decodeFromString<ChallengeAdminDto>(jsonWithoutFinalizedAt)

        assertNull(dto.finalizedAt)
        assertNull(dto.createdBy)
        assertNull(dto.description)
        assertNull(dto.publishedAt)
        assertNull(dto.cancelledAt)
    }

    @Test
    fun `ChallengeAdminDto cu campuri JSON necunoscute - sunt ignorate, nu crapa`() {
        val jsonWithExtraField = """
            {
                "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "title": "Weekend Golf Hunt",
                "targetFamilyId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "requiredPosts": 5,
                "rewardPoints": 300,
                "startsAt": "2026-08-07T00:00:00Z",
                "endsAt": "2026-08-09T00:00:00Z",
                "adminTimezone": "Europe/Bucharest",
                "status": "DRAFT",
                "createdAt": "2026-08-01T00:00:00Z",
                "updatedAt": "2026-08-01T00:00:00Z",
                "participantCount": 12,
                "someNewField": "unexpected"
            }
        """.trimIndent()
        val dto = json.decodeFromString<ChallengeAdminDto>(jsonWithExtraField)

        assertEquals("Weekend Golf Hunt", dto.title)
    }

    @Test
    fun `ChallengeListCursorDto se deserializeaza corect`() {
        val cursorJson = """
            {
                "lastCreatedAt": "2026-08-01T00:00:00Z",
                "lastChallengeId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
            }
        """.trimIndent()
        val dto = json.decodeFromString<ChallengeListCursorDto>(cursorJson)

        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), dto.lastCreatedAt)
        assertEquals(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), dto.lastChallengeId)
    }

    @Test
    fun `ChallengeAdminPageDto cu pagina completa - se deserializeaza corect`() {
        val pageJson = """
            {
                "challenges": [$challengeAdminJson],
                "nextCursor": {
                    "lastCreatedAt": "2026-08-01T00:00:00Z",
                    "lastChallengeId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                },
                "hasMore": true
            }
        """.trimIndent()
        val dto = json.decodeFromString<ChallengeAdminPageDto>(pageJson)

        assertEquals(1, dto.challenges.size)
        assertEquals("Weekend Golf Hunt", dto.challenges[0].title)
        assertTrue(dto.hasMore)
        assertEquals(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), dto.nextCursor?.lastChallengeId)
    }

    @Test
    fun `ChallengeAdminPageDto fara elemente si fara cursor - lista goala, nu crapa`() {
        val pageJson = """{ "challenges": [], "hasMore": false }"""
        val dto = json.decodeFromString<ChallengeAdminPageDto>(pageJson)

        assertTrue(dto.challenges.isEmpty())
        assertNull(dto.nextCursor)
        assertEquals(false, dto.hasMore)
    }

    @Test
    fun `RevokeResultDto se deserializeaza corect`() {
        val dto = json.decodeFromString<RevokeResultDto>("""{ "revokedCount": 4 }""")
        assertEquals(4, dto.revokedCount)
    }

    @Test
    fun `FinalizationResultDto se deserializeaza corect`() {
        val dto = json.decodeFromString<FinalizationResultDto>(
            """{ "grantedCount": 3, "revokedCount": 1 }""",
        )
        assertEquals(3, dto.grantedCount)
        assertEquals(1, dto.revokedCount)
    }

    @Test
    fun `CreateChallengeAdminRequestDto se serializeaza cu toate campurile`() {
        val request = CreateChallengeAdminRequestDto(
            title = "Weekend Golf Hunt",
            description = "Find every Golf you can this weekend",
            targetFamilyId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            requiredPosts = 5,
            rewardPoints = 300,
            startsAtLocal = "2026-08-07T09:00:00",
            endsAtLocal = "2026-08-09T22:00:00",
            timezone = "Europe/Bucharest",
        )
        val encoded = json.encodeToString(CreateChallengeAdminRequestDto.serializer(), request)
        val decoded = json.decodeFromString(CreateChallengeAdminRequestDto.serializer(), encoded)

        assertEquals(request, decoded)
    }

    @Test
    fun `CreateChallengeAdminRequestDto fara description - description este null`() {
        val requestJson = """
            {
                "title": "Weekend Golf Hunt",
                "targetFamilyId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "requiredPosts": 5,
                "rewardPoints": 300,
                "startsAtLocal": "2026-08-07T09:00:00",
                "endsAtLocal": "2026-08-09T22:00:00",
                "timezone": "Europe/Bucharest"
            }
        """.trimIndent()
        val dto = json.decodeFromString<CreateChallengeAdminRequestDto>(requestJson)

        assertNull(dto.description)
    }

    @Test
    fun `UpdateChallengeTitleRequestDto se serializeaza cu toate campurile`() {
        val request = UpdateChallengeTitleRequestDto(title = "New title", description = "New description")
        val encoded = json.encodeToString(UpdateChallengeTitleRequestDto.serializer(), request)
        val decoded = json.decodeFromString(UpdateChallengeTitleRequestDto.serializer(), encoded)

        assertEquals(request, decoded)
    }

    @Test
    fun `RevokeAllRequestDto se serializeaza cu confirmChallengeId`() {
        val id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val request = RevokeAllRequestDto(confirmChallengeId = id)
        val encoded = json.encodeToString(RevokeAllRequestDto.serializer(), request)
        val decoded = json.decodeFromString(RevokeAllRequestDto.serializer(), encoded)

        assertEquals(id, decoded.confirmChallengeId)
    }
}
