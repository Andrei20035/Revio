package com.revio.app.data.remote.dto.leaderboard

import com.revio.app.features.leaderboard.RankMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class LeaderboardResponseDtoMappingTest {

    private val sampleEntryDto = LeaderboardEntryDto(
        userId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        rank = 1,
        username = "testuser",
        avatarUrl = "https://cdn.example.com/avatar.jpg",
        spotScore = 1500,
        streakDays = 7,
    )

    @Test
    fun `entry toDomain maps all fields correctly`() {
        val domain = sampleEntryDto.toDomain()
        assertEquals(sampleEntryDto.userId, domain.userId)
        assertEquals(1, domain.rank)
        assertEquals("testuser", domain.username)
        assertEquals("https://cdn.example.com/avatar.jpg", domain.avatarUrl)
        assertEquals(1500, domain.spotScore)
        assertEquals(7, domain.streakDays)
    }

    @Test
    fun `entry toDomain preserves null avatarUrl`() {
        val domain = sampleEntryDto.copy(avatarUrl = null).toDomain()
        assertNull(domain.avatarUrl)
    }

    @Test
    fun `currentUser toDomain maps movement UP`() {
        val dto = CurrentUserStandingDto(entry = sampleEntryDto, movement = "UP", placesMoved = 3)
        assertEquals(RankMovement.UP, dto.toDomain().movement)
        assertEquals(3, dto.toDomain().placesMoved)
    }

    @Test
    fun `currentUser toDomain maps movement DOWN`() {
        val dto = CurrentUserStandingDto(entry = sampleEntryDto, movement = "DOWN", placesMoved = 2)
        assertEquals(RankMovement.DOWN, dto.toDomain().movement)
    }

    @Test
    fun `currentUser toDomain maps movement KEEP`() {
        val dto = CurrentUserStandingDto(entry = sampleEntryDto, movement = "KEEP", placesMoved = 0)
        assertEquals(RankMovement.KEEP, dto.toDomain().movement)
    }

    @Test
    fun `currentUser toDomain maps lowercase movement`() {
        val dto = CurrentUserStandingDto(entry = sampleEntryDto, movement = "up", placesMoved = 1)
        assertEquals(RankMovement.UP, dto.toDomain().movement)
    }

    @Test
    fun `currentUser toDomain maps unknown movement to KEEP`() {
        val dto = CurrentUserStandingDto(entry = sampleEntryDto, movement = "UNKNOWN", placesMoved = 0)
        assertEquals(RankMovement.KEEP, dto.toDomain().movement)
    }

    @Test
    fun `currentUser toDomain default movement and placesMoved`() {
        val dto = CurrentUserStandingDto(entry = sampleEntryDto)
        assertEquals(RankMovement.KEEP, dto.toDomain().movement)
        assertEquals(0, dto.toDomain().placesMoved)
    }

    @Test
    fun `response toDomain with empty entries list`() {
        val dto = LeaderboardResponseDto(
            currentUser = CurrentUserStandingDto(entry = sampleEntryDto),
            entries = emptyList(),
        )
        val domain = dto.toDomain()
        assertEquals(emptyList<Any>(), domain.entries)
    }

    @Test
    fun `response toDomain maps all entries`() {
        val entry2 = sampleEntryDto.copy(
            userId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            rank = 2,
            username = "second",
        )
        val dto = LeaderboardResponseDto(
            currentUser = CurrentUserStandingDto(entry = sampleEntryDto),
            entries = listOf(sampleEntryDto, entry2),
        )
        val domain = dto.toDomain()
        assertEquals(2, domain.entries.size)
        assertEquals("testuser", domain.entries[0].username)
        assertEquals("second", domain.entries[1].username)
    }
}
