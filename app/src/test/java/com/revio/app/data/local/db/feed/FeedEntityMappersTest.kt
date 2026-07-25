package com.revio.app.data.local.db.feed

import com.revio.app.data.model.FeedPost
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FeedPostEntity.createdAtIso] must round-trip through [FeedPost.toEntity]/[FeedPostEntity.toDomain]
 * at full nanosecond precision — the whole point of storing [Instant.toString] verbatim instead
 * of an epoch-millis long is that the keyset pagination cursor never silently truncates.
 */
class FeedEntityMappersTest {

    private fun post(createdAt: Instant) = FeedPost(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        username = "someone",
        brand = "Porsche",
        model = "911",
        imageUrl = "https://example.com/x.jpg",
        caption = null,
        latitude = null,
        longitude = null,
        createdAt = createdAt,
        likeCount = 0,
        commentCount = 0,
        likedByCurrentUser = false,
    )

    @Test
    fun `createdAt supravietuieste round-trip-ului la precizie de nanosecunda`() {
        val precise = Instant.parse("2026-03-14T09:26:53.987654321Z")
        val original = post(precise)

        val roundTripped = original.toEntity(position = 0).toDomain()

        assertEquals(precise, roundTripped.createdAt)
        assertEquals(987654321, roundTripped.createdAt.nano)
    }

    @Test
    fun `createdAtEpochMs singur nu ar fi suficient - epochMilli trunchiaza sub-milisecunda`() {
        val precise = Instant.parse("2026-03-14T09:26:53.987654321Z")
        val entity = post(precise).toEntity(position = 0)

        // Demonstrates why createdAtIso, not createdAtEpochMs, is what the domain mapper reads:
        // the epoch-millis column alone would lose everything past the third decimal.
        val truncated = Instant.ofEpochMilli(entity.createdAtEpochMs)
        assertEquals(987000000, truncated.nano)
        assertEquals(987654321, entity.toDomain().createdAt.nano)
    }

    @Test
    fun `id-ul ramane acelasi UUID dupa round-trip`() {
        val original = post(Instant.now())

        val roundTripped = original.toEntity(position = 3).toDomain()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.userId, roundTripped.userId)
    }
}
