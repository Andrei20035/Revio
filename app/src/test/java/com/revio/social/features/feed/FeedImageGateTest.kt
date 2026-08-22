package com.revio.social.features.feed

import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.data.image.PrefetchOutcome
import com.revio.social.data.model.FeedPost
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for [FeedImageGate]: §8.1-§8.4 (ordering/skip, append-only publication,
 * bounded concurrency, ready-ahead buffer) plus §8.5-§8.7 (per-attempt timeout with a single
 * transient retry, the first-paint deadline, reconnect requeueing, and the bounded refill signal).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedImageGateTest {

    private fun post(
        label: String,
        likeCount: Long = 0,
        commentCount: Long = 0,
        likedByCurrentUser: Boolean = false,
    ) = FeedPost(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        username = "user-$label",
        brand = "Porsche",
        model = "911",
        imageUrl = "https://example.com/$label.jpg",
        caption = null,
        latitude = null,
        longitude = null,
        createdAt = Instant.now(),
        likeCount = likeCount,
        commentCount = commentCount,
        likedByCurrentUser = likedByCurrentUser,
    )

    @Test
    fun `un esec permanent este sarit fara sa blocheze postarile urmatoare`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        val b = post("B")
        val c = post("C")
        val d = post("D")
        val e = post("E")

        fake.outcomes[b.imageUrl] = PrefetchOutcome.PermanentFailure("404")
        fake.hold(e.imageUrl)

        gate.onCandidates(listOf(a, b, c, d, e), resetToken = 0)
        runCurrent()

        assertEquals(listOf(a.id, c.id, d.id), gate.visiblePosts.value.map { it.id })
        assertTrue("E ar trebui să fie încă în fetch", fake.fetchCalls.contains(e.imageUrl))
        assertEquals(1, fake.activeFetchCount)
    }

    @Test
    fun `postarile deja cached se publica imediat fara niciun apel fetch`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val posts = listOf(post("A"), post("B"), post("C"))
        posts.forEach { fake.cachedUrls += it.imageUrl }

        gate.onCandidates(posts, resetToken = 0)
        runCurrent()

        assertEquals(posts.map { it.id }, gate.visiblePosts.value.map { it.id })
        assertTrue(fake.fetchCalls.isEmpty())
    }

    @Test
    fun `cel mult 3 fetch-uri ruleaza simultan`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val posts = listOf(post("A"), post("B"), post("C"), post("D"), post("E"))
        posts.forEach { fake.hold(it.imageUrl) }

        gate.onCandidates(posts, resetToken = 0)
        runCurrent()

        assertEquals(3, fake.fetchCalls.size)
        assertEquals(3, fake.activeFetchCount)
        assertTrue(fake.maxObservedConcurrency <= 3)

        // Freeing one slot lets exactly one more (the 4th candidate) start, never exceeding 3 at once.
        fake.release(posts[0].imageUrl)
        runCurrent()

        assertEquals(4, fake.fetchCalls.size)
        assertTrue(fake.maxObservedConcurrency <= 3)
    }

    @Test
    fun `visiblePosts este mereu o subsecventa ordonata a candidatilor`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val posts = (1..8).map { post("P$it") }
        // A couple of permanent failures scattered through the list.
        fake.outcomes[posts[2].imageUrl] = PrefetchOutcome.PermanentFailure("403")
        fake.outcomes[posts[5].imageUrl] = PrefetchOutcome.PermanentFailure("404")

        gate.onCandidates(posts, resetToken = 0)
        runCurrent()

        val candidateIndex = posts.mapIndexed { index, p -> p.id to index }.toMap()
        val visibleIndices = gate.visiblePosts.value.map { candidateIndex.getValue(it.id) }
        assertEquals(visibleIndices.sorted(), visibleIndices)
        assertTrue(visibleIndices.distinct().size == visibleIndices.size)
        // The two permanently-failed posts must never appear.
        assertTrue(posts[2].id !in gate.visiblePosts.value.map { it.id })
        assertTrue(posts[5].id !in gate.visiblePosts.value.map { it.id })
    }

    @Test
    fun `publicarea este append-only cand se adauga o pagina noua`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val firstPage = listOf(post("A"), post("B"), post("C"))
        gate.onCandidates(firstPage, resetToken = 0)
        runCurrent()
        val afterFirstPage = gate.visiblePosts.value

        assertEquals(firstPage.map { it.id }, afterFirstPage.map { it.id })

        val secondPage = firstPage + listOf(post("D"), post("E"))
        gate.onCandidates(secondPage, resetToken = 0) // same resetToken == append, not a reset
        runCurrent()
        val afterSecondPage = gate.visiblePosts.value

        assertEquals(afterFirstPage, afterSecondPage.take(afterFirstPage.size))
        assertEquals(secondPage.map { it.id }, afterSecondPage.map { it.id })
    }

    @Test
    fun `un reset token nou goleste visiblePosts si reporneste gating-ul`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val firstBatch = listOf(post("A"), post("B"))
        gate.onCandidates(firstBatch, resetToken = 0)
        runCurrent()
        assertEquals(2, gate.visiblePosts.value.size)

        val refreshedBatch = listOf(post("X"), post("Y"))
        gate.onCandidates(refreshedBatch, resetToken = 1) // resetToken bump == replaceWithFirstPage
        runCurrent()

        assertEquals(refreshedBatch.map { it.id }, gate.visiblePosts.value.map { it.id })
    }

    @Test
    fun `aceeasi lista de candidati cu likeCount schimbat improspateaza visiblePosts`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A", likeCount = 5, likedByCurrentUser = false)
        gate.onCandidates(listOf(a), resetToken = 0)
        runCurrent()
        assertEquals(5L, gate.visiblePosts.value.first().likeCount)
        assertEquals(false, gate.visiblePosts.value.first().likedByCurrentUser)

        val updated = a.copy(likeCount = 6, likedByCurrentUser = true)
        gate.onCandidates(listOf(updated), resetToken = 0) // same resetToken == in-place refresh
        runCurrent()

        assertEquals(6L, gate.visiblePosts.value.first().likeCount)
        assertEquals(true, gate.visiblePosts.value.first().likedByCurrentUser)
    }

    @Test
    fun `commentCount schimbat se reflecta in visiblePosts`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A", commentCount = 2)
        gate.onCandidates(listOf(a), resetToken = 0)
        runCurrent()
        assertEquals(2L, gate.visiblePosts.value.first().commentCount)

        val updated = a.copy(commentCount = 3)
        gate.onCandidates(listOf(updated), resetToken = 0)
        runCurrent()

        assertEquals(3L, gate.visiblePosts.value.first().commentCount)
    }

    @Test
    fun `o postare disparuta din candidati ramane in visiblePosts`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        val b = post("B")
        gate.onCandidates(listOf(a, b), resetToken = 0)
        runCurrent()
        assertEquals(listOf(a.id, b.id), gate.visiblePosts.value.map { it.id })

        gate.onCandidates(listOf(b), resetToken = 0) // A trimmed from candidates, same resetToken
        runCurrent()

        assertEquals(listOf(a.id, b.id), gate.visiblePosts.value.map { it.id })
    }

    @Test
    fun `ordinea si frontiera nu se schimba la remapare`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        val b = post("B", likeCount = 1)
        val c = post("C")
        gate.onCandidates(listOf(a, b, c), resetToken = 0)
        runCurrent()
        assertEquals(listOf(a.id, b.id, c.id), gate.visiblePosts.value.map { it.id })

        val updatedB = b.copy(likeCount = 2)
        gate.onCandidates(listOf(a, updatedB, c), resetToken = 0)
        runCurrent()

        assertEquals(listOf(a.id, b.id, c.id), gate.visiblePosts.value.map { it.id })
        assertEquals(2L, gate.visiblePosts.value[1].likeCount)
    }

    @Test
    fun `bufferul de Ready opreste fetch-uri noi cand capul listei e blocat`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val head = post("HEAD")
        val followers = (1..9).map { post("F$it") }
        val posts = listOf(head) + followers
        fake.hold(head.imageUrl) // blocks the publish frontier forever

        gate.onCandidates(posts, resetToken = 0)
        runCurrent()

        // Nothing can publish — the head never resolves.
        assertTrue(gate.visiblePosts.value.isEmpty())
        // The cap (READY_BUFFER_TARGET + MAX_CONCURRENT_FETCHES = 6) must have kicked in well
        // before every follower got a fetch call.
        assertTrue(
            "fetchCalls=${fake.fetchCalls.size}, expected <= 6",
            fake.fetchCalls.size <= 6,
        )
        assertTrue(fake.fetchCalls.size < posts.size)
        assertTrue(followers.last().imageUrl !in fake.fetchCalls)
    }

    @Test
    fun `un request agatat devine terminal dupa timeout si prefixul avanseaza`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        val b = post("B")
        fake.hold(a.imageUrl) // never resolves — simulates a hung request

        gate.onCandidates(listOf(a, b), resetToken = 0)
        runCurrent()

        assertTrue(gate.visiblePosts.value.isEmpty()) // A still blocks the frontier
        assertEquals(1, fake.fetchCalls.count { it == a.imageUrl }) // first attempt in flight

        advanceTimeBy(10_001) // past IMAGE_FETCH_TIMEOUT_MS — first attempt times out
        runCurrent()
        advanceTimeBy(301) // past TRANSIENT_RETRY_DELAY_MS — second attempt starts
        runCurrent()
        assertEquals(2, fake.fetchCalls.count { it == a.imageUrl }) // exactly one retry

        advanceTimeBy(10_001) // second attempt also times out
        runCurrent()

        // A is now a terminal (retryable) skip — the frontier moved past it onto B.
        assertEquals(listOf(b.id), gate.visiblePosts.value.map { it.id })
    }

    @Test
    fun `un esec tranzitoriu are exact doua incercari apoi Skipped retryable`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        val b = post("B")
        fake.outcomes[a.imageUrl] = PrefetchOutcome.TransientFailure("503")

        gate.onCandidates(listOf(a, b), resetToken = 0)
        runCurrent()
        advanceTimeBy(301) // past TRANSIENT_RETRY_DELAY_MS
        runCurrent()

        assertEquals(2, fake.fetchCalls.count { it == a.imageUrl })
        // A never succeeds (still transient) — it's skipped, not blocking B.
        assertEquals(listOf(b.id), gate.visiblePosts.value.map { it.id })
    }

    @Test
    fun `un esec permanent are o singura incercare`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        val b = post("B")
        fake.outcomes[a.imageUrl] = PrefetchOutcome.PermanentFailure("404")

        gate.onCandidates(listOf(a, b), resetToken = 0)
        runCurrent()
        advanceTimeBy(301) // if a retry were (wrongly) scheduled, this would let it run
        runCurrent()

        assertEquals(1, fake.fetchCalls.count { it == a.imageUrl })
        assertEquals(listOf(b.id), gate.visiblePosts.value.map { it.id })
    }

    @Test
    fun `capul blocat este sarit dupa deadline-ul de first paint cand visiblePosts e goala`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        val b = post("B")
        fake.hold(a.imageUrl)
        fake.cachedUrls += b.imageUrl // resolves instantly once the frontier reaches it

        gate.onCandidates(listOf(a, b), resetToken = 0)
        runCurrent()

        assertTrue(gate.visiblePosts.value.isEmpty())

        advanceTimeBy(5_001) // past FIRST_PAINT_DEADLINE_MS
        runCurrent()

        // A was demoted to a retryable skip purely because nothing had painted yet — B publishes.
        assertEquals(listOf(b.id), gate.visiblePosts.value.map { it.id })
        // A's own fetch is still running in the background (never released) — not cancelled.
        assertEquals(1, fake.activeFetchCount)
    }

    @Test
    fun `deadline-ul de first paint nu se aplica daca visiblePosts nu e goala`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        val b = post("B")
        fake.cachedUrls += a.imageUrl // publishes immediately
        fake.hold(b.imageUrl) // stuck behind an already-non-empty visiblePosts

        gate.onCandidates(listOf(a, b), resetToken = 0)
        runCurrent()

        assertEquals(listOf(a.id), gate.visiblePosts.value.map { it.id })

        // Past FIRST_PAINT_DEADLINE_MS but under IMAGE_FETCH_TIMEOUT_MS — isolates the first-paint
        // watcher (which must not fire here) from the unrelated per-attempt timeout.
        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(listOf(a.id), gate.visiblePosts.value.map { it.id })
        assertEquals(1, fake.activeFetchCount) // B still genuinely fetching, never demoted
    }

    @Test
    fun `onReconnect reincearca doar Skipped retryable nedepasite de frontiera`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val x1 = post("X1") // resolves fast — publishes
        val x2 = post("X2") // transient failure, gets passed by the frontier before the block
        val block = post("BLOCK") // held forever — the actual frontier blocker
        val y = post("Y") // transient failure, but stuck behind `block` — not yet passed

        fake.outcomes[x2.imageUrl] = PrefetchOutcome.TransientFailure("503")
        fake.outcomes[y.imageUrl] = PrefetchOutcome.TransientFailure("503")
        fake.hold(block.imageUrl)

        gate.onCandidates(listOf(x1, x2, block, y), resetToken = 0)
        runCurrent()
        advanceTimeBy(301) // lets x2's and y's single retry play out
        runCurrent()

        assertEquals(listOf(x1.id), gate.visiblePosts.value.map { it.id })
        val x2CallsBeforeReconnect = fake.fetchCalls.count { it == x2.imageUrl }
        val yCallsBeforeReconnect = fake.fetchCalls.count { it == y.imageUrl }
        assertEquals(2, x2CallsBeforeReconnect) // one attempt + one retry
        assertEquals(2, yCallsBeforeReconnect)

        // Network comes back — Y would succeed now, but X2 has already been passed by the frontier.
        fake.outcomes[y.imageUrl] = PrefetchOutcome.Success
        gate.onReconnect()
        runCurrent()

        // Y was requeued and fetched again; X2 (already behind the frontier) was left alone.
        assertEquals(yCallsBeforeReconnect + 1, fake.fetchCalls.count { it == y.imageUrl })
        assertEquals(x2CallsBeforeReconnect, fake.fetchCalls.count { it == x2.imageUrl })
    }

    @Test
    fun `refill-ul este cerut cel mult MAX_REFILL_PAGES ori per ciclu`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        var refillCalls = 0
        val gate = FeedImageGate(fake, backgroundScope, onRefillNeeded = { refillCalls++ })

        val page1 = listOf(post("A"), post("B"), post("C"))
        gate.onCandidates(page1, resetToken = 0, hasMore = true)
        runCurrent()
        assertEquals(1, refillCalls) // fewer than READY_BUFFER_TARGET candidates remain — asks once

        val page2 = page1 + listOf(post("D"), post("E"), post("F"))
        gate.onCandidates(page2, resetToken = 0, hasMore = true) // the requested page "landed"
        runCurrent()
        assertEquals(2, refillCalls) // still thin — asks again (2nd of MAX_REFILL_PAGES)

        val page3 = page2 + listOf(post("G"), post("H"), post("I"))
        gate.onCandidates(page3, resetToken = 0, hasMore = true)
        runCurrent()
        assertEquals(2, refillCalls) // cap reached — no third request
    }

    @Test
    fun `refill-ul nu porneste deloc cand hasMore este false`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        var refillCalls = 0
        val gate = FeedImageGate(fake, backgroundScope, onRefillNeeded = { refillCalls++ })

        gate.onCandidates(listOf(post("A")), resetToken = 0, hasMore = false)
        runCurrent()

        assertEquals(0, refillCalls)
    }

    @Test
    fun `offline total cu cache gol marcheaza toti candidatii Skipped fara niciun fetch`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        var networkAvailable = false
        val gate = FeedImageGate(fake, backgroundScope, isNetworkAvailable = { networkAvailable })

        val posts = (1..5).map { post("P$it") }

        gate.onCandidates(posts, resetToken = 0)
        runCurrent()

        assertTrue(fake.fetchCalls.isEmpty())
        assertTrue(gate.visiblePosts.value.isEmpty())
    }

    @Test
    fun `offline cu unele imagini pe disc publica doar pe cele cached`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        var networkAvailable = false
        val gate = FeedImageGate(fake, backgroundScope, isNetworkAvailable = { networkAvailable })

        val a = post("A")
        val b = post("B")
        val c = post("C")
        val d = post("D")
        fake.cachedUrls += a.imageUrl
        fake.cachedUrls += c.imageUrl

        gate.onCandidates(listOf(a, b, c, d), resetToken = 0)
        runCurrent()

        assertTrue(fake.fetchCalls.isEmpty())
        // B is offline-skipped but doesn't block the frontier — C (also cached) still publishes.
        assertEquals(listOf(a.id, c.id), gate.visiblePosts.value.map { it.id })
    }

    @Test
    fun `offline cu toti candidatii esuati produce ExhaustedNoContent chiar daca hasMore este true`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        var networkAvailable = false
        val gate = FeedImageGate(fake, backgroundScope, isNetworkAvailable = { networkAvailable })

        val posts = (1..5).map { post("P$it") }

        gate.onCandidates(posts, resetToken = 0, hasMore = true)
        runCurrent()

        assertEquals(GateStatus.ExhaustedNoContent, gate.status.value)
    }

    @Test
    fun `onReconnect dupa o fereastra offline reia fetch-urile pentru candidatii nedepasiti de frontiera`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        var networkAvailable = true
        val gate = FeedImageGate(fake, backgroundScope, isNetworkAvailable = { networkAvailable })

        val a = post("A") // held while online — blocks the frontier, keeps a slot in flight
        val b = post("B")
        fake.hold(a.imageUrl)

        gate.onCandidates(listOf(a), resetToken = 0)
        runCurrent()
        assertTrue(gate.visiblePosts.value.isEmpty())

        // Network drops while A is still in flight; B arrives during the offline window.
        networkAvailable = false
        gate.onCandidates(listOf(a, b), resetToken = 0)
        runCurrent()

        assertTrue(fake.fetchCalls.none { it == b.imageUrl })

        // Reconnect: A is still blocking the frontier (index 0), so B (index 1, not yet passed)
        // gets requeued and fetched.
        networkAvailable = true
        gate.onReconnect()
        runCurrent()

        assertTrue(fake.fetchCalls.contains(b.imageUrl))
        assertTrue(gate.visiblePosts.value.isEmpty()) // A still blocks publication

        // Releasing A lets both publish, in order.
        fake.release(a.imageUrl)
        runCurrent()

        assertEquals(listOf(a.id, b.id), gate.visiblePosts.value.map { it.id })
    }

    // ----------------------------------------------------------------------
    // pas 2.6c — ev. 23: feed_image_gate, reason nu mai e aruncat, permanent vs tranzitoriu
    // ----------------------------------------------------------------------

    @Test
    fun `esec permanent - feed_image_gate cu outcome permanent si reason-ul clasificarii`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val analyticsClient = mockk<AnalyticsClient>(relaxed = true)
        val gate = FeedImageGate(fake, backgroundScope, analyticsClient = analyticsClient)

        val a = post("A")
        fake.outcomes[a.imageUrl] = PrefetchOutcome.PermanentFailure("http_404")

        gate.onCandidates(listOf(a), resetToken = 0)
        runCurrent()

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "feed_image_gate",
                    params = mapOf(
                        "outcome" to AnalyticsParamValue.StringValue("permanent"),
                        "reason" to AnalyticsParamValue.StringValue("http_404"),
                    ),
                )
            )
        }
    }

    @Test
    fun `esec tranzitoriu epuizat dupa retry - feed_image_gate cu outcome transient`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val analyticsClient = mockk<AnalyticsClient>(relaxed = true)
        val gate = FeedImageGate(fake, backgroundScope, analyticsClient = analyticsClient)

        val a = post("A")
        fake.outcomes[a.imageUrl] = PrefetchOutcome.TransientFailure("http_5xx")

        gate.onCandidates(listOf(a), resetToken = 0)
        runCurrent()
        advanceTimeBy(301) // past TRANSIENT_RETRY_DELAY_MS
        runCurrent()

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "feed_image_gate",
                    params = mapOf(
                        "outcome" to AnalyticsParamValue.StringValue("transient"),
                        "reason" to AnalyticsParamValue.StringValue("http_5xx"),
                    ),
                )
            )
        }
    }

    @Test
    fun `succes - nu se genereaza feed_image_gate`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val analyticsClient = mockk<AnalyticsClient>(relaxed = true)
        val gate = FeedImageGate(fake, backgroundScope, analyticsClient = analyticsClient)

        val a = post("A")
        gate.onCandidates(listOf(a), resetToken = 0)
        runCurrent()

        verify(exactly = 0) { analyticsClient.log(match { it.name == "feed_image_gate" }) }
    }

    // ----------------------------------------------------------------------
    // pas 5 — removePost: the one deliberate exception to append-only publication
    // ----------------------------------------------------------------------

    @Test
    fun `removePost scoate din visiblePosts o postare deja publicata`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        val b = post("B")
        val c = post("C")
        gate.onCandidates(listOf(a, b, c), resetToken = 0)
        runCurrent()
        assertEquals(listOf(a.id, b.id, c.id), gate.visiblePosts.value.map { it.id })

        gate.removePost(b.id)
        runCurrent()

        assertEquals(listOf(a.id, c.id), gate.visiblePosts.value.map { it.id })
    }

    @Test
    fun `removePost pe o postare inca nepublicata nu blocheaza frontiera pentru restul`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        val b = post("B")
        val c = post("C")
        fake.hold(a.imageUrl) // A never resolves on its own — still head-of-line.

        gate.onCandidates(listOf(a, b, c), resetToken = 0)
        runCurrent()
        assertTrue(gate.visiblePosts.value.isEmpty())

        gate.removePost(a.id)
        runCurrent()

        assertEquals(listOf(b.id, c.id), gate.visiblePosts.value.map { it.id })
    }

    @Test
    fun `dupa removePost, un candidat ulterior identic ramane exclus`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        val b = post("B")
        gate.onCandidates(listOf(a, b), resetToken = 0)
        runCurrent()
        assertEquals(listOf(a.id, b.id), gate.visiblePosts.value.map { it.id })

        gate.removePost(a.id)
        runCurrent()
        assertEquals(listOf(b.id), gate.visiblePosts.value.map { it.id })

        // Same resetToken == append semantics; the cache's next emission (post gone from Room)
        // no longer includes A among the candidates either.
        gate.onCandidates(listOf(b), resetToken = 0)
        runCurrent()

        assertEquals(listOf(b.id), gate.visiblePosts.value.map { it.id })
    }

    @Test
    fun `removePost pentru un id necunoscut nu modifica visiblePosts`() = runTest {
        val fake = FakeFeedImagePrefetcher()
        val gate = FeedImageGate(fake, backgroundScope)

        val a = post("A")
        gate.onCandidates(listOf(a), resetToken = 0)
        runCurrent()
        assertEquals(listOf(a.id), gate.visiblePosts.value.map { it.id })

        gate.removePost(UUID.randomUUID())
        runCurrent()

        assertEquals(listOf(a.id), gate.visiblePosts.value.map { it.id })
    }
}
