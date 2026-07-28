package com.revio.social.data.local.db.feed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.social.data.local.db.RevioDatabase
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room-backed [FeedDao] behavior that can't be verified against [FakeFeedCache]-style JVM
 * doubles: append-time position stability, the single-transaction replace (no empty-list
 * flicker), trim eviction, and that a like update doesn't reshuffle ordering.
 */
@RunWith(AndroidJUnit4::class)
class FeedDaoTest {

    private lateinit var db: RevioDatabase
    private lateinit var dao: FeedDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RevioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.feedDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(id: String, position: Int, createdAt: Instant = Instant.now(), caption: String? = null) =
        FeedPostEntity(
            id = id,
            userId = "user-$id",
            username = "username-$id",
            authorProfilePictureUrl = null,
            brand = "Porsche",
            model = "911",
            imageUrl = "https://example.com/$id.jpg",
            caption = caption,
            latitude = null,
            longitude = null,
            createdAtIso = createdAt.toString(),
            createdAtEpochMs = createdAt.toEpochMilli(),
            likeCount = 0,
            commentCount = 0,
            likedByCurrentUser = false,
            locationLabel = null,
            authorIsEarlySpotter = false,
            authorEarlySpotterNumber = null,
            position = position,
        )

    private fun meta(hasMore: Boolean, maxPosition: Int) = FeedMetaEntity(
        nextCursorCreatedAt = null,
        nextCursorPostId = null,
        hasMore = hasMore,
        lastSyncedAtEpochMs = System.currentTimeMillis(),
        ownerUserId = null,
        maxPosition = maxPosition,
    )

    @Test
    fun appendPage_pastreaza_pozitia_existenta_pentru_un_id_duplicat() = runBlocking {
        dao.replaceWithFirstPage(
            posts = listOf(entity("a", position = 0), entity("b", position = 1)),
            meta = meta(hasMore = true, maxPosition = 1),
        )

        // "a" reappears (server page window shifted) with different content; "c" is genuinely new.
        dao.appendPage(
            posts = listOf(entity("a", position = 0, caption = "updated"), entity("c", position = 0)),
            meta = meta(hasMore = false, maxPosition = -1),
        )

        assertEquals(0, dao.positionOf("a"))
        assertEquals(1, dao.positionOf("b"))
        assertEquals(2, dao.positionOf("c"))

        val posts = dao.observePosts().first()
        assertEquals(listOf("a", "b", "c"), posts.map { it.id })
        assertEquals("updated", posts.first { it.id == "a" }.caption)
    }

    @Test
    fun replaceWithFirstPage_emite_o_singura_data_fara_flicker_de_lista_goala() = runBlocking {
        dao.replaceWithFirstPage(
            posts = listOf(entity("a", position = 0)),
            meta = meta(hasMore = true, maxPosition = 0),
        )

        val emissions = mutableListOf<List<FeedPostEntity>>()
        val job = launch { dao.observePosts().collect { emissions.add(it) } }
        delay(100) // let the initial emission (current state: ["a"]) land

        dao.replaceWithFirstPage(
            posts = listOf(entity("b", position = 0)),
            meta = meta(hasMore = false, maxPosition = 0),
        )
        delay(200) // let the single post-transaction emission land

        job.cancel()

        assertEquals(2, emissions.size) // initial state, then exactly one update — never an empty list in between
        assertTrue("nicio emisie nu ar trebui sa fie o lista goala", emissions.none { it.isEmpty() })
        assertEquals(listOf("b"), emissions.last().map { it.id })
    }

    @Test
    fun trimTo_pastreaza_cele_mai_mici_pozitii() = runBlocking {
        dao.replaceWithFirstPage(
            posts = (0..4).map { entity("p$it", position = it) },
            meta = meta(hasMore = true, maxPosition = 4),
        )

        dao.trimTo(3)

        val remaining = dao.observePosts().first()
        assertEquals(listOf("p0", "p1", "p2"), remaining.map { it.id })
    }

    @Test
    fun updateLike_nu_deranjeaza_ordinea() = runBlocking {
        dao.replaceWithFirstPage(
            posts = listOf(entity("a", position = 0), entity("b", position = 1), entity("c", position = 2)),
            meta = meta(hasMore = false, maxPosition = 2),
        )

        dao.updateLike("b", liked = true, likeCount = 7)

        val posts = dao.observePosts().first()
        assertEquals(listOf("a", "b", "c"), posts.map { it.id })
        val updated = posts.first { it.id == "b" }
        assertTrue(updated.likedByCurrentUser)
        assertEquals(7L, updated.likeCount)
    }

    @Test
    fun clearAll_goleste_postarile_si_metadatele() = runBlocking {
        dao.replaceWithFirstPage(
            posts = listOf(entity("a", position = 0)),
            meta = meta(hasMore = true, maxPosition = 0),
        )

        dao.clearAll()

        assertTrue(dao.observePosts().first().isEmpty())
        assertEquals(null, dao.getMeta())
    }
}
