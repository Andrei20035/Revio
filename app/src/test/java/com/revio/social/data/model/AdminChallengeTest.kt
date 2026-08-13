package com.revio.social.data.model

import com.revio.social.data.remote.dto.admin.challenge.ChallengeAdminDto
import com.revio.social.data.remote.dto.admin.challenge.ChallengeAdminPageDto
import com.revio.social.data.remote.dto.admin.challenge.ChallengeListCursorDto
import com.revio.social.data.remote.dto.admin.challenge.FinalizationResultDto
import com.revio.social.data.remote.dto.admin.challenge.RevokeResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class AdminChallengeTest {

    private val challengeId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val familyId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val createdBy = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")

    private fun challengeAdminDto(status: String) = ChallengeAdminDto(
        id = challengeId,
        title = "Weekend Golf Hunt",
        description = "Find every Golf you can this weekend",
        targetFamilyId = familyId,
        requiredPosts = 5,
        rewardPoints = 300,
        startsAt = Instant.parse("2026-08-07T00:00:00Z"),
        endsAt = Instant.parse("2026-08-09T00:00:00Z"),
        adminTimezone = "Europe/Bucharest",
        status = status,
        createdBy = createdBy,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-02T00:00:00Z"),
        publishedAt = Instant.parse("2026-08-02T00:00:00Z"),
        cancelledAt = null,
        finalizedAt = null,
    )

    @Test
    fun `status DRAFT se mapeaza la ChallengeAdminStatus DRAFT`() {
        assertEquals(ChallengeAdminStatus.DRAFT, challengeAdminDto("DRAFT").toDomain().status)
    }

    @Test
    fun `status SCHEDULED se mapeaza la ChallengeAdminStatus SCHEDULED`() {
        assertEquals(ChallengeAdminStatus.SCHEDULED, challengeAdminDto("SCHEDULED").toDomain().status)
    }

    @Test
    fun `status CANCELLED se mapeaza la ChallengeAdminStatus CANCELLED`() {
        assertEquals(ChallengeAdminStatus.CANCELLED, challengeAdminDto("CANCELLED").toDomain().status)
    }

    @Test
    fun `status lowercase se mapeaza corect`() {
        assertEquals(ChallengeAdminStatus.SCHEDULED, challengeAdminDto("scheduled").toDomain().status)
    }

    @Test
    fun `status necunoscut se mapeaza la ChallengeAdminStatus UNKNOWN, nu crapa`() {
        assertEquals(ChallengeAdminStatus.UNKNOWN, challengeAdminDto("SOMETHING_NEW").toDomain().status)
    }

    @Test
    fun `status ACTIVE (efectiv, nu persistat) se mapeaza la UNKNOWN`() {
        // The admin DTO's status is the persisted ChallengeStatus (DRAFT/SCHEDULED/CANCELLED),
        // never the derived effective status — ACTIVE/ENDED never arrive here.
        assertEquals(ChallengeAdminStatus.UNKNOWN, challengeAdminDto("ACTIVE").toDomain().status)
    }

    @Test
    fun `ChallengeAdminDto se mapeaza complet la AdminChallenge`() {
        val domain = challengeAdminDto("DRAFT").toDomain()

        assertEquals(challengeId, domain.id)
        assertEquals("Weekend Golf Hunt", domain.title)
        assertEquals("Find every Golf you can this weekend", domain.description)
        assertEquals(familyId, domain.targetFamilyId)
        assertEquals(5, domain.requiredPosts)
        assertEquals(300, domain.rewardPoints)
        assertEquals(Instant.parse("2026-08-07T00:00:00Z"), domain.startsAt)
        assertEquals(Instant.parse("2026-08-09T00:00:00Z"), domain.endsAt)
        assertEquals("Europe/Bucharest", domain.adminTimezone)
        assertEquals(createdBy, domain.createdBy)
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), domain.createdAt)
        assertEquals(Instant.parse("2026-08-02T00:00:00Z"), domain.updatedAt)
        assertEquals(Instant.parse("2026-08-02T00:00:00Z"), domain.publishedAt)
        assertNull(domain.cancelledAt)
        assertNull(domain.finalizedAt)
    }

    @Test
    fun `ChallengeListCursorDto se mapeaza la AdminChallengeListCursor`() {
        val dto = ChallengeListCursorDto(
            lastCreatedAt = Instant.parse("2026-08-01T00:00:00Z"),
            lastChallengeId = challengeId,
        )
        val domain = dto.toDomain()

        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), domain.lastCreatedAt)
        assertEquals(challengeId, domain.lastChallengeId)
    }

    @Test
    fun `ChallengeAdminPageDto cu cursor - se mapeaza corect`() {
        val dto = ChallengeAdminPageDto(
            challenges = listOf(challengeAdminDto("SCHEDULED")),
            nextCursor = ChallengeListCursorDto(
                lastCreatedAt = Instant.parse("2026-08-01T00:00:00Z"),
                lastChallengeId = challengeId,
            ),
            hasMore = true,
        )
        val domain = dto.toDomain()

        assertEquals(1, domain.challenges.size)
        assertEquals(ChallengeAdminStatus.SCHEDULED, domain.challenges[0].status)
        assertTrue(domain.hasMore)
        assertEquals(challengeId, domain.nextCursor?.lastChallengeId)
    }

    @Test
    fun `ChallengeAdminPageDto fara cursor - nextCursor este null, nu crapa`() {
        val dto = ChallengeAdminPageDto(challenges = emptyList(), nextCursor = null, hasMore = false)
        val domain = dto.toDomain()

        assertTrue(domain.challenges.isEmpty())
        assertNull(domain.nextCursor)
    }

    @Test
    fun `RevokeResultDto se mapeaza la AdminChallengeRevokeResult`() {
        val domain = RevokeResultDto(revokedCount = 4).toDomain()
        assertEquals(4, domain.revokedCount)
    }

    @Test
    fun `FinalizationResultDto se mapeaza la AdminChallengeFinalizationResult`() {
        val domain = FinalizationResultDto(grantedCount = 3, revokedCount = 1).toDomain()
        assertEquals(3, domain.grantedCount)
        assertEquals(1, domain.revokedCount)
    }
}
