package com.revio.social.data.remote.dto.challenge

import com.revio.social.data.model.RewardState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ChallengeDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val activeChallengeJson = """
        {
            "challenge": {
                "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "title": "Weekend Golf Hunt",
                "description": "Find every Golf you can this weekend",
                "targetFamilyBrand": "Volkswagen",
                "targetFamilyName": "Golf",
                "requiredPosts": 5,
                "rewardPoints": 300,
                "startsAt": "2026-08-07T00:00:00Z",
                "endsAt": "2026-08-09T00:00:00Z"
            },
            "progress": {
                "contributionCount": 3,
                "rewardState": "NONE"
            }
        }
    """.trimIndent()

    @Test
    fun `current cu challenge activ - se deserializeaza corect`() {
        val dto = json.decodeFromString<CurrentChallengeDto>(activeChallengeJson)
        val domain = dto.toDomain()

        assertEquals(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), domain.challenge?.id)
        assertEquals("Weekend Golf Hunt", domain.challenge?.title)
        assertEquals("Find every Golf you can this weekend", domain.challenge?.description)
        assertEquals("Volkswagen", domain.challenge?.targetFamilyBrand)
        assertEquals("Golf", domain.challenge?.targetFamilyName)
        assertEquals(5, domain.challenge?.requiredPosts)
        assertEquals(300, domain.challenge?.rewardPoints)
        assertEquals(Instant.parse("2026-08-07T00:00:00Z"), domain.challenge?.startsAt)
        assertEquals(Instant.parse("2026-08-09T00:00:00Z"), domain.challenge?.endsAt)
        assertEquals(3, domain.progress?.contributionCount)
        assertEquals(RewardState.NONE, domain.progress?.rewardState)
    }

    @Test
    fun `current fara challenge programat - challenge si progress sunt null`() {
        val noChallengeJson = """{ "challenge": null, "progress": null }"""
        val dto = json.decodeFromString<CurrentChallengeDto>(noChallengeJson)
        val domain = dto.toDomain()

        assertNull(domain.challenge)
        assertNull(domain.progress)
    }

    @Test
    fun `current cu description null - se deserializeaza fara sa crape`() {
        val jsonNoDescription = """
            {
                "challenge": {
                    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "title": "Weekend Golf Hunt",
                    "description": null,
                    "targetFamilyBrand": "Volkswagen",
                    "targetFamilyName": "Golf",
                    "requiredPosts": 5,
                    "rewardPoints": 300,
                    "startsAt": "2026-08-07T00:00:00Z",
                    "endsAt": "2026-08-09T00:00:00Z"
                },
                "progress": { "contributionCount": 0, "rewardState": "NONE" }
            }
        """.trimIndent()
        val dto = json.decodeFromString<CurrentChallengeDto>(jsonNoDescription)
        assertNull(dto.toDomain().challenge?.description)
    }

    @Test
    fun `rewardState GRANTED se mapeaza la RewardState GRANTED`() {
        val progress = ChallengeProgressDto(contributionCount = 5, rewardState = "GRANTED")
        assertEquals(RewardState.GRANTED, progress.toDomain().rewardState)
    }

    @Test
    fun `rewardState necunoscut se mapeaza la RewardState UNKNOWN, nu crapa`() {
        val progress = ChallengeProgressDto(contributionCount = 5, rewardState = "SOMETHING_NEW")
        assertEquals(RewardState.UNKNOWN, progress.toDomain().rewardState)
    }

    @Test
    fun `rewardState lowercase se mapeaza corect`() {
        val progress = ChallengeProgressDto(contributionCount = 1, rewardState = "granted")
        assertEquals(RewardState.GRANTED, progress.toDomain().rewardState)
    }

    @Test
    fun `campuri JSON necunoscute sunt ignorate, nu crapa`() {
        val jsonWithExtraField = """
            {
                "challenge": {
                    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "title": "Weekend Golf Hunt",
                    "targetFamilyBrand": "Volkswagen",
                    "targetFamilyName": "Golf",
                    "requiredPosts": 5,
                    "rewardPoints": 300,
                    "startsAt": "2026-08-07T00:00:00Z",
                    "endsAt": "2026-08-09T00:00:00Z",
                    "status": "SCHEDULED",
                    "adminTimezone": "Europe/Bucharest"
                },
                "progress": { "contributionCount": 0, "rewardState": "NONE", "someNewField": 42 }
            }
        """.trimIndent()
        val dto = json.decodeFromString<CurrentChallengeDto>(jsonWithExtraField)
        assertEquals("Weekend Golf Hunt", dto.toDomain().challenge?.title)
    }

    @Test
    fun `progress detail cu contributii - se deserializeaza corect`() {
        val progressDetailJson = """
            {
                "progress": { "contributionCount": 2, "rewardState": "NONE" },
                "contributions": [
                    { "postId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "createdAt": "2026-08-07T10:00:00Z" },
                    { "postId": "cccccccc-cccc-cccc-cccc-cccccccccccc", "createdAt": "2026-08-08T11:30:00Z" }
                ]
            }
        """.trimIndent()
        val dto = json.decodeFromString<ChallengeProgressDetailDto>(progressDetailJson)
        val domain = dto.toDomain()

        assertEquals(2, domain.progress.contributionCount)
        assertEquals(2, domain.contributions.size)
        assertEquals(
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            domain.contributions[0].postId,
        )
        assertEquals(Instant.parse("2026-08-08T11:30:00Z"), domain.contributions[1].createdAt)
    }

    @Test
    fun `progress detail fara contributii - lista goala, nu crapa`() {
        val progressDetailJson = """
            { "progress": { "contributionCount": 0, "rewardState": "NONE" } }
        """.trimIndent()
        val dto = json.decodeFromString<ChallengeProgressDetailDto>(progressDetailJson)
        assertTrue(dto.toDomain().contributions.isEmpty())
    }
}
