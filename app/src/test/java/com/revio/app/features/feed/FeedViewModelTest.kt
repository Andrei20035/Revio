package com.revio.app.features.feed

import com.revio.app.MainDispatcherRule
import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.ERROR_CODE_NETWORK
import com.revio.app.core.network.NETWORK_ERROR_MESSAGE
import com.revio.app.core.network.NetworkConnectivityManager
import com.revio.app.data.local.preferences.UserPreferences
import com.revio.app.data.model.FeedPost
import com.revio.app.data.model.LikeStatus
import com.revio.app.data.model.ReportReason
import com.revio.app.data.remote.dto.post.FeedCursor
import com.revio.app.data.remote.dto.post.FeedResult
import com.revio.app.data.repository.CommentRepository
import com.revio.app.data.repository.LikeRepository
import com.revio.app.data.repository.PostRepository
import com.revio.app.data.repository.ReportRepository
import com.revio.app.data.repository.UserRepository
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Covers the offline-capable feed cache/connectivity contract from the implementation plan
 * (§5's FeedViewModelTest list): no network call while the cache-empty NoInternet state shows,
 * exactly-one-load guarding via the Job guard, silent-sync gating on staleness/scroll position,
 * owner-mismatch cache wipes, and the offline guards on likes/comments/report.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val ownerUserId: UUID = UUID.randomUUID()

    private lateinit var postRepository: PostRepository
    private lateinit var userRepository: UserRepository
    private lateinit var reportRepository: ReportRepository
    private lateinit var likeRepository: LikeRepository
    private lateinit var commentRepository: CommentRepository
    private lateinit var feedCache: FakeFeedCache
    private lateinit var networkAvailable: MutableStateFlow<Boolean>
    private lateinit var internetValidated: MutableStateFlow<Boolean>
    private lateinit var connectivity: NetworkConnectivityManager
    private lateinit var userPreferences: UserPreferences

    @Before
    fun setup() {
        postRepository = mockk()
        userRepository = mockk {
            every { currentUser } returns MutableStateFlow(null)
            coEvery { getCurrentUser() } returns ApiResult.Error("unused in these tests")
        }
        reportRepository = mockk()
        likeRepository = mockk()
        commentRepository = mockk()
        feedCache = FakeFeedCache()
        networkAvailable = MutableStateFlow(false)
        internetValidated = MutableStateFlow(false)
        connectivity = mockk {
            every { isNetworkAvailable } returns networkAvailable
            every { isInternetValidated } returns internetValidated
        }
        userPreferences = mockk {
            every { userId } returns flowOf(ownerUserId)
        }
    }

    private fun createViewModel() = FeedViewModel(
        postRepository = postRepository,
        userRepository = userRepository,
        reportRepository = reportRepository,
        likeRepository = likeRepository,
        commentRepository = commentRepository,
        feedCache = feedCache,
        connectivity = connectivity,
        userPreferences = userPreferences,
    )

    private fun post(
        id: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        likeCount: Long = 0,
        commentCount: Long = 0,
        liked: Boolean = false,
        createdAt: Instant = Instant.now(),
    ) = FeedPost(
        id = id,
        userId = userId,
        username = "user-$id",
        brand = "Porsche",
        model = "911",
        imageUrl = "https://example.com/$id.jpg",
        caption = null,
        latitude = null,
        longitude = null,
        createdAt = createdAt,
        likeCount = likeCount,
        commentCount = commentCount,
        likedByCurrentUser = liked,
    )

    private fun feedResult(
        posts: List<FeedPost>,
        nextCursor: FeedCursor? = null,
        hasMore: Boolean = false,
    ) = FeedResult(posts = posts, nextCursor = nextCursor, hasMore = hasMore)

    // ---- 1. cache gol + offline ----

    @Test
    fun `cache gol si offline arata NoInternet fara niciun apel de retea`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(FeedContent.NoInternet, vm.uiState.value.content)
        coVerify(exactly = 0) { postRepository.getFeedPosts(any(), any()) }
    }

    // ---- 2. reconectare din starea NoInternet ----

    @Test
    fun `reconectarea din NoInternet declanseaza exact un load si arata postarile`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(FeedContent.NoInternet, vm.uiState.value.content)

        val result = feedResult(posts = listOf(post()))
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns ApiResult.Success(result)

        networkAvailable.value = true
        internetValidated.value = true
        advanceUntilIdle()

        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        coVerify(exactly = 1) { postRepository.getFeedPosts(limit = 15, cursor = null) }
    }

    // ---- 3. cache nevid + offline la startup ----

    @Test
    fun `cache nevid si offline la startup arata postarile imediat fara apel`() = runTest {
        val cachedPost = post()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(cachedPost), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )

        val loadingValues = mutableListOf<Boolean>()
        val vm = createViewModel()
        val job = launch { vm.uiState.collect { loadingValues.add(it.isLoadingInitial) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertTrue("isLoadingInitial nu ar trebui sa devina niciodata true", loadingValues.none { it })
        coVerify(exactly = 0) { postRepository.getFeedPosts(any(), any()) }
    }

    // ---- 4. footer retry offline, repetat ----

    @Test
    fun `footer retry offline nu atinge reteaua nici la incercari repetate`() = runTest {
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = true),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(FeedFooterState.Idle, vm.uiState.value.footer)

        // The first tap is what actually produces OfflineRetry (load() sets loadMoreError
        // after hitting the offline pre-flight check) — hydration alone never does.
        repeat(5) {
            vm.onFooterRetry()
            advanceUntilIdle()
        }

        assertEquals(FeedFooterState.OfflineRetry, vm.uiState.value.footer)
        coVerify(exactly = 0) { postRepository.getFeedPosts(any(), any()) }
    }

    // ---- 5. footer retry online, apel unic; al doilea tap in zbor nu dubleaza ----

    @Test
    fun `footer retry online face un singur apel si un al doilea tap in zbor nu dubleaza`() = runTest {
        val cursor = FeedCursor(lastCreatedAt = Instant.now(), lastPostId = UUID.randomUUID())
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), nextCursor = cursor, hasMore = true),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true

        val deferred = CompletableDeferred<ApiResult<FeedResult>>()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = cursor) } coAnswers { deferred.await() }

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onFooterRetry()
        advanceUntilIdle()
        assertEquals(FeedFooterState.Loading, vm.uiState.value.footer)

        // A second tap while the first is still in flight must be a no-op (Job guard).
        vm.onFooterRetry()
        advanceUntilIdle()

        deferred.complete(ApiResult.Success(feedResult(posts = listOf(post()), hasMore = false)))
        advanceUntilIdle()

        assertEquals(FeedFooterState.CaughtUp, vm.uiState.value.footer)
        coVerify(exactly = 1) { postRepository.getFeedPosts(limit = 15, cursor = cursor) }
    }

    // ---- 6. loadNextPage() + onFooterRetry() in aceeasi fereastra ----

    @Test
    fun `loadNextPage si footer retry aproape simultan produc un singur apel`() = runTest {
        val cursor = FeedCursor(lastCreatedAt = Instant.now(), lastPostId = UUID.randomUUID())
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), nextCursor = cursor, hasMore = true),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true

        val deferred = CompletableDeferred<ApiResult<FeedResult>>()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = cursor) } coAnswers { deferred.await() }

        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadNextPage()
        vm.onFooterRetry()
        advanceUntilIdle()

        deferred.complete(ApiResult.Success(feedResult(posts = listOf(post()), hasMore = false)))
        advanceUntilIdle()

        coVerify(exactly = 1) { postRepository.getFeedPosts(limit = 15, cursor = cursor) }
    }

    // ---- 7. refresh() esuat nu modifica cache-ul ----

    @Test
    fun `refresh esuat pastreaza cache-ul neschimbat`() = runTest {
        val originalPost = post()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(originalPost), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Error("Server error")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf(originalPost), vm.uiState.value.feedPosts)
    }

    // ---- 8. reconectare cu cache proaspat (< 30 min) nu declanseaza nimic ----

    @Test
    fun `reconectare cu cache proaspat nu declanseaza niciun apel`() = runTest {
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now().minus(Duration.ofMinutes(5)),
        )
        val vm = createViewModel()
        advanceUntilIdle()

        networkAvailable.value = true
        internetValidated.value = true
        advanceUntilIdle()

        coVerify(exactly = 0) { postRepository.getFeedPosts(any(), any()) }
    }

    // ---- 9. reconectare cu cache stale + lista in varf -> refresh discret ----

    @Test
    fun `reconectare cu cache stale langa varful listei face un refresh discret fara spinner`() = runTest {
        val staleSyncedAt = Instant.now().minus(Duration.ofMinutes(31))
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = staleSyncedAt,
        )
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onScrollPositionChanged(0)

        val refreshingValues = mutableListOf<Boolean>()
        val contentValues = mutableListOf<FeedContent>()
        val job = launch {
            vm.uiState.collect {
                refreshingValues.add(it.isRefreshing)
                contentValues.add(it.content)
            }
        }

        val freshPost = post()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(freshPost), hasMore = false))

        networkAvailable.value = true
        internetValidated.value = true
        advanceUntilIdle()
        job.cancel()

        assertTrue("isRefreshing nu ar trebui sa devina niciodata true", refreshingValues.none { it })
        assertTrue("content nu ar trebui sa treaca prin Skeletons", contentValues.none { it is FeedContent.Skeletons })
        assertFalse(vm.uiState.value.isSyncing)
        assertEquals(listOf(freshPost), vm.uiState.value.feedPosts)
    }

    // ---- 10. refresh discret esuat e complet silentios ----

    @Test
    fun `refresh discret esuat nu schimba cache-ul si nu afiseaza niciun mesaj`() = runTest {
        val staleSyncedAt = Instant.now().minus(Duration.ofMinutes(31))
        val originalPost = post()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(originalPost), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = staleSyncedAt,
        )
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onScrollPositionChanged(0)

        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Error("Server error")

        networkAvailable.value = true
        internetValidated.value = true
        advanceUntilIdle()

        assertEquals(listOf(originalPost), vm.uiState.value.feedPosts)
        assertNull(vm.uiState.value.userMessage)
        assertNull(vm.uiState.value.refreshError)
    }

    // ---- 11. cache stale, dar derulat departe de varf -> fara apel ----

    @Test
    fun `reconectare cu cache stale si lista derulata departe de varf nu face nimic`() = runTest {
        val staleSyncedAt = Instant.now().minus(Duration.ofMinutes(31))
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = staleSyncedAt,
        )
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onScrollPositionChanged(40) // > PAGE_SIZE (15)

        networkAvailable.value = true
        internetValidated.value = true
        advanceUntilIdle()

        coVerify(exactly = 0) { postRepository.getFeedPosts(any(), any()) }
    }

    // ---- 12. doua reconectari in fereastra de debounce -> un singur load ----

    @Test
    fun `doua reconectari rapide in fereastra de debounce produc un singur load`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(FeedContent.NoInternet, vm.uiState.value.content)

        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(post()), hasMore = false))

        networkAvailable.value = true
        internetValidated.value = true
        advanceTimeBy(50)
        internetValidated.value = false
        internetValidated.value = true
        advanceUntilIdle()

        coVerify(exactly = 1) { postRepository.getFeedPosts(limit = 15, cursor = null) }
    }

    // ---- 13. owner mismatch -> clear() si cache tratat ca gol ----

    @Test
    fun `owner mismatch goleste cache-ul si trateaza feedul ca gol`() = runTest {
        val otherOwner = UUID.randomUUID()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = otherOwner,
            syncedAt = Instant.now(),
        )

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, feedCache.clearCount)
        assertEquals(FeedContent.NoInternet, vm.uiState.value.content)
        coVerify(exactly = 0) { postRepository.getFeedPosts(any(), any()) }
    }

    // ---- 14. like online: optimist, revert la eroare, autoritativ la succes ----

    @Test
    fun `like online scrie optimist si reconciliaza cu raspunsul serverului la succes`() = runTest {
        val target = post(likeCount = 5, liked = false)
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(target), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { likeRepository.toggleLike(target.id) } returns ApiResult.Success(LikeStatus(liked = true, count = 6))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onLikeToggle(target.id)
        advanceUntilIdle()

        val updated = vm.uiState.value.feedPosts.first { it.id == target.id }
        assertTrue(updated.likedByCurrentUser)
        assertEquals(6L, updated.likeCount)
        assertTrue(vm.uiState.value.likeInFlight.isEmpty())
    }

    @Test
    fun `like online revine la starea initiala cand serverul respinge`() = runTest {
        val target = post(likeCount = 5, liked = false)
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(target), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { likeRepository.toggleLike(target.id) } returns ApiResult.Error("nope")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onLikeToggle(target.id)
        advanceUntilIdle()

        val reverted = vm.uiState.value.feedPosts.first { it.id == target.id }
        assertFalse(reverted.likedByCurrentUser)
        assertEquals(5L, reverted.likeCount)
        assertEquals("Couldn't update your like. Please try again.", vm.uiState.value.userMessage)
    }

    // ---- 15. like offline: fara apel, fara scriere in cache, mesaj afisat ----

    @Test
    fun `like offline nu apeleaza reteaua si nu modifica cache-ul`() = runTest {
        val target = post(likeCount = 5, liked = false)
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(target), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        // networkAvailable stays false.
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onLikeToggle(target.id)
        advanceUntilIdle()

        val unchanged = vm.uiState.value.feedPosts.first { it.id == target.id }
        assertFalse(unchanged.likedByCurrentUser)
        assertEquals(5L, unchanged.likeCount)
        assertEquals("You're offline — likes will work once you're back online.", vm.uiState.value.userMessage)
        coVerify(exactly = 0) { likeRepository.toggleLike(any()) }
    }

    // ---- 16. comentariu offline: draft pastrat, fara apel ----

    @Test
    fun `submitComment offline pastreaza draftul si nu apeleaza reteaua`() = runTest {
        val target = post()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(target), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        coEvery { commentRepository.getCommentsForPost(target.id) } returns ApiResult.Success(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openComments(target.id)
        advanceUntilIdle()
        vm.onCommentDraftChange("hello there")

        vm.submitComment()
        advanceUntilIdle()

        assertEquals("hello there", vm.uiState.value.commentsSheet?.draft)
        assertFalse(vm.uiState.value.commentsSheet?.isSubmitting ?: true)
        assertEquals("You're offline — your comment wasn't posted.", vm.uiState.value.userMessage)
        coVerify(exactly = 0) { commentRepository.addComment(any(), any()) }
    }

    // ---- 17. report offline: dialog inchis, fara apel ----

    @Test
    fun `confirmReport offline inchide dialogul si nu apeleaza reteaua`() = runTest {
        val target = post()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(target), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onReportReasonSelected(target.id, ReportReason.INAPPROPRIATE_CONTENT)
        vm.confirmReport()
        advanceUntilIdle()

        assertNull(vm.uiState.value.reportDialog)
        assertEquals("You're offline — your report wasn't sent.", vm.uiState.value.userMessage)
        coVerify(exactly = 0) { reportRepository.reportPost(any(), any()) }
    }

    // ---- 18. eroare de retea la comentarii -> reconectarea nu reincarca automat ----

    @Test
    fun `eroare de retea la comentarii nu se reincarca automat la reconectare`() = runTest {
        val target = post()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(target), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        coEvery { commentRepository.getCommentsForPost(target.id) } returns
            ApiResult.Error(NETWORK_ERROR_MESSAGE, code = ERROR_CODE_NETWORK)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openComments(target.id)
        advanceUntilIdle()

        assertEquals("You're offline — comments couldn't be loaded.", vm.uiState.value.commentsSheet?.errorMessage)

        networkAvailable.value = true
        internetValidated.value = true
        advanceUntilIdle()

        coVerify(exactly = 1) { commentRepository.getCommentsForPost(target.id) }
    }

    // ---- 19. cursorul e pasat verbatim (precizie de nanosecunda) la pagina urmatoare ----

    @Test
    fun `loadNextPage paseaza cursorul din uiState verbatim catre repository`() = runTest {
        val preciseCursor = FeedCursor(
            lastCreatedAt = Instant.parse("2026-01-01T10:15:30.123456789Z"),
            lastPostId = UUID.randomUUID(),
        )
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), nextCursor = preciseCursor, hasMore = true),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true

        val capturedCursor: CapturingSlot<FeedCursor> = slot()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = capture(capturedCursor)) } returns
            ApiResult.Success(feedResult(posts = emptyList(), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadNextPage()
        advanceUntilIdle()

        assertEquals(preciseCursor.lastCreatedAt, capturedCursor.captured.lastCreatedAt)
        assertEquals(preciseCursor.lastPostId, capturedCursor.captured.lastPostId)
    }
}
