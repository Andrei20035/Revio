package com.revio.social.features.feed

import com.revio.social.MainDispatcherRule
import com.revio.social.core.analytics.AnalyticsClient
import com.revio.social.core.analytics.AnalyticsEvent
import com.revio.social.core.analytics.AnalyticsParamValue
import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.ERROR_CODE_NETWORK
import com.revio.social.core.network.NETWORK_ERROR_MESSAGE
import com.revio.social.core.network.NetworkConnectivityManager
import com.revio.social.data.image.PrefetchOutcome
import com.revio.social.data.local.preferences.UserPreferences
import com.revio.social.data.model.Comment
import com.revio.social.data.model.FeedPost
import com.revio.social.data.model.LikeStatus
import com.revio.social.data.model.ReportReason
import com.revio.social.data.remote.dto.post.FeedCursor
import com.revio.social.data.remote.dto.post.FeedResult
import com.revio.social.data.repository.CommentRepository
import com.revio.social.data.repository.LikeRepository
import com.revio.social.data.repository.PostRepository
import com.revio.social.data.repository.ReportRepository
import com.revio.social.data.repository.UserRepository
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private lateinit var feedImagePrefetcher: FakeFeedImagePrefetcher
    private lateinit var analyticsClient: AnalyticsClient

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
        feedImagePrefetcher = FakeFeedImagePrefetcher()
        analyticsClient = mockk(relaxed = true)
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
        feedImagePrefetcher = feedImagePrefetcher,
        analyticsClient = analyticsClient,
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

    /**
     * Collects the full, consecutively-deduplicated sequence of [FeedContent] the ViewModel
     * emits, for asserting transitions rather than only the final state. Uses
     * [UnconfinedTestDispatcher] so every emission is observed — [MainDispatcherRule] already
     * runs `viewModelScope` unconfined, but the collector itself must be too, or conflation could
     * hide the exact race this is meant to catch. Launched on [backgroundScope] so the never-
     * completing `collect` doesn't fail the test with an [kotlinx.coroutines.test.UncompletedCoroutinesError] —
     * `backgroundScope` is cancelled automatically when the test body returns.
     */
    private fun TestScope.collectContent(vm: FeedViewModel): List<FeedContent> {
        val seen = mutableListOf<FeedContent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect { state -> if (seen.lastOrNull() != state.content) seen += state.content }
        }
        return seen
    }

    /** Empty is only ever allowed to be the final content — never a transient value passed through. */
    private fun List<FeedContent>.assertNoTransientEmpty() {
        val emptyIndex = indexOfFirst { it is FeedContent.Empty }
        assertTrue(
            "Empty nu are voie sa apara decat ca stare finala: $this",
            emptyIndex == -1 || emptyIndex == lastIndex,
        )
    }

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

        val contentValues = mutableListOf<FeedContent>()
        val vm = createViewModel()
        val job = launch { vm.uiState.collect { contentValues.add(it.content) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertTrue(
            "continutul nu ar trebui sa arate skeletonuri cand cache-ul are deja postari",
            contentValues.none { it is FeedContent.Skeletons },
        )
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

    // ---- 5b. footer retry cu hasMore = false nu face niciun apel ----

    @Test
    fun `footer retry cu hasMore false nu face niciun apel`() = runTest {
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(FeedFooterState.CaughtUp, vm.uiState.value.footer)

        vm.onFooterRetry()
        advanceUntilIdle()

        coVerify(exactly = 0) { postRepository.getFeedPosts(any(), any()) }
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

    // ---- 7a2. refresh(force = true) anuleaza un load in zbor in loc sa renunte (pas 6) ----

    @Test
    fun `refresh force = true anuleaza un loadNextPage in zbor si tot executa un fetch`() = runTest {
        val cursor = FeedCursor(lastCreatedAt = Instant.now(), lastPostId = UUID.randomUUID())
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), nextCursor = cursor, hasMore = true),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true

        val hangingLoadMore = CompletableDeferred<ApiResult<FeedResult>>()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = cursor) } coAnswers { hangingLoadMore.await() }
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(post()), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadNextPage()
        advanceUntilIdle()
        assertTrue("load-ul de paginare trebuie sa fie in zbor", vm.uiState.value.isLoadingMore)

        vm.refresh(force = true)
        advanceUntilIdle()

        // Fara force, acest refresh ar fi fost un no-op (Job guard). Cu force = true, load-ul
        // in zbor este anulat si refresh-ul isi executa propriul apel de retea.
        coVerify(exactly = 1) { postRepository.getFeedPosts(limit = 15, cursor = null) }
        assertFalse(vm.uiState.value.isRefreshing)
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    // ---- 7b. refresh() esuat (eroare server) afiseaza mesaj si poate fi consumat ----

    @Test
    fun `refresh esuat cu eroare server afiseaza mesaj consumabil`() = runTest {
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
        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertFalse(vm.uiState.value.isRefreshing)
        assertNotNull(vm.uiState.value.refreshError)
        assertNotNull(vm.uiState.value.userMessage)

        vm.consumeRefreshError()
        vm.consumeUserMessage()

        assertNull(vm.uiState.value.refreshError)
        assertNull(vm.uiState.value.userMessage)
    }

    // ---- 7c. refresh() offline afiseaza mesaj si poate fi consumat ----

    @Test
    fun `refresh offline afiseaza mesaj consumabil`() = runTest {
        val originalPost = post()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(originalPost), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = false
        internetValidated.value = false

        val vm = createViewModel()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf(originalPost), vm.uiState.value.feedPosts)
        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(LoadError.Offline, vm.uiState.value.refreshError)
        assertNotNull(vm.uiState.value.userMessage)
        coVerify(exactly = 0) { postRepository.getFeedPosts(any(), any()) }

        vm.consumeRefreshError()
        vm.consumeUserMessage()

        assertNull(vm.uiState.value.refreshError)
        assertNull(vm.uiState.value.userMessage)
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

    // ---- 10b. sync silentios care intoarce zero postari avanseaza freshness fara sa goleasca cache-ul ----

    @Test
    fun `sync silentios gol avanseaza freshness fara sa goleasca cache-ul`() = runTest {
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
            ApiResult.Success(feedResult(posts = emptyList(), hasMore = false))

        networkAvailable.value = true
        internetValidated.value = true
        advanceUntilIdle()

        assertEquals(1, feedCache.markSyncedCount)
        assertEquals(0, feedCache.clearCount)
        assertEquals(listOf(originalPost), vm.uiState.value.feedPosts)
        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertEquals(FeedPhase.ShowingPosts, vm.uiState.value.phase)

        // A second validated reconnect right after should not re-fire: the TTL was consumed.
        internetValidated.value = false
        internetValidated.value = true
        advanceUntilIdle()

        coVerify(exactly = 1) { postRepository.getFeedPosts(limit = 15, cursor = null) }
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

    // ---- 13b. userId intarziat, dar sub timeout -> owner-ul intarziat e totusi scris in meta ----

    @Test
    fun `userId intarziat dar sub timeout este scris corect in meta la primul load`() = runTest {
        val delayedUserId = MutableStateFlow<UUID?>(null)
        userPreferences = mockk {
            every { userId } returns delayedUserId
        }
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(post()), hasMore = false))

        val vm = createViewModel()
        advanceTimeBy(500)
        delayedUserId.value = ownerUserId
        advanceUntilIdle()

        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertEquals(ownerUserId, feedCache.meta?.ownerUserId)
    }

    // ---- 13c. userId ramane null dincolo de timeout -> pagina e totusi scrisa, cu owner null in meta ----

    @Test
    fun `userId ramas null dupa timeout scrie pagina cu owner null in meta`() = runTest {
        userPreferences = mockk {
            every { userId } returns MutableStateFlow<UUID?>(null)
        }
        networkAvailable.value = true
        internetValidated.value = true
        val freshPost = post()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(freshPost), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()

        // Skipping the write here would leave the UI stuck on skeletons forever (see plan §"cauze
        // secundare"); a cache persisted with a null owner is instead treated as a mismatch and
        // wiped the next time hydrateFromCache() resolves a real owner id.
        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertEquals(listOf(freshPost), vm.uiState.value.feedPosts)
        assertNull(feedCache.meta?.ownerUserId)
    }

    // ---- 13d. owner null in meta pe cache nevid -> tratat ca mismatch implicit ----

    @Test
    fun `owner null in meta pe cache nevid goleste cache-ul ca un mismatch`() = runTest {
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = null,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        val freshPost = post()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(freshPost), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, feedCache.clearCount)
        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertEquals(listOf(freshPost), vm.uiState.value.feedPosts)
        coVerify(exactly = 1) { postRepository.getFeedPosts(limit = 15, cursor = null) }
    }

    // ---- 13e. owner potrivit in meta -> fara wipe (fara regresie pe calea fericita) ----

    @Test
    fun `owner potrivit in meta nu declanseaza clear`() = runTest {
        val cachedPost = post()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(cachedPost), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(0, feedCache.clearCount)
        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertEquals(listOf(cachedPost), vm.uiState.value.feedPosts)
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
        val rendered = vm.uiState.value.visiblePosts.first { it.id == target.id }
        assertTrue(rendered.likedByCurrentUser)
        assertEquals(6L, rendered.likeCount)
        // pas 5.7
        verify(exactly = 1) {
            analyticsClient.log(match { it.name == "feed_like_result" && it.params["outcome"] == AnalyticsParamValue.StringValue("success") })
        }
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
        val renderedReverted = vm.uiState.value.visiblePosts.first { it.id == target.id }
        assertFalse(renderedReverted.likedByCurrentUser)
        assertEquals(5L, renderedReverted.likeCount)
        // pas 5.7
        verify(exactly = 1) {
            analyticsClient.log(match { it.name == "feed_like_result" && it.params["outcome"] == AnalyticsParamValue.StringValue("failure") })
        }
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
        // Cached so the image gate can publish it to visiblePosts despite networkAvailable staying false.
        feedImagePrefetcher.cachedUrls += target.imageUrl
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
        val renderedUnchanged = vm.uiState.value.visiblePosts.first { it.id == target.id }
        assertFalse(renderedUnchanged.likedByCurrentUser)
        assertEquals(5L, renderedUnchanged.likeCount)
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

    @Test
    fun `submitComment online loghează feed_comment_result la succes (pas 5_7)`() = runTest {
        val target = post()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(target), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { commentRepository.getCommentsForPost(target.id) } returns ApiResult.Success(emptyList())
        coEvery { commentRepository.addComment(target.id, "hello there") } returns ApiResult.Success(
            Comment(id = UUID.randomUUID(), userId = ownerUserId, postId = target.id, username = "me", profilePictureUrl = null, text = "hello there", createdAt = Instant.now())
        )

        val vm = createViewModel()
        advanceUntilIdle()
        vm.openComments(target.id)
        advanceUntilIdle()
        vm.onCommentDraftChange("hello there")

        vm.submitComment()
        advanceUntilIdle()

        verify(exactly = 1) {
            analyticsClient.log(match { it.name == "feed_comment_result" && it.params["outcome"] == AnalyticsParamValue.StringValue("success") })
        }
    }

    @Test
    fun `submitComment success actualizeaza commentCount in visiblePosts`() = runTest {
        val target = post(commentCount = 2)
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(target), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        // openComments -> loadComments sets commentCount from the authoritative list size, so it
        // must match the post's starting commentCount (2) for the post-submit assertion (3) below.
        val existingComments = listOf(
            Comment(id = UUID.randomUUID(), userId = UUID.randomUUID(), postId = target.id, username = "a", profilePictureUrl = null, text = "first", createdAt = Instant.now()),
            Comment(id = UUID.randomUUID(), userId = UUID.randomUUID(), postId = target.id, username = "b", profilePictureUrl = null, text = "second", createdAt = Instant.now()),
        )
        coEvery { commentRepository.getCommentsForPost(target.id) } returns ApiResult.Success(existingComments)
        coEvery { commentRepository.addComment(target.id, "hello there") } returns ApiResult.Success(
            Comment(id = UUID.randomUUID(), userId = ownerUserId, postId = target.id, username = "me", profilePictureUrl = null, text = "hello there", createdAt = Instant.now())
        )

        val vm = createViewModel()
        advanceUntilIdle()
        vm.openComments(target.id)
        advanceUntilIdle()
        vm.onCommentDraftChange("hello there")

        vm.submitComment()
        advanceUntilIdle()

        val updated = vm.uiState.value.feedPosts.first { it.id == target.id }
        assertEquals(3L, updated.commentCount)
        val rendered = vm.uiState.value.visiblePosts.first { it.id == target.id }
        assertEquals(3L, rendered.commentCount)
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

    @Test
    fun `confirmReport online loghează feed_report_result la succes (pas 5_7)`() = runTest {
        val target = post()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(target), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { reportRepository.reportPost(target.id, ReportReason.INAPPROPRIATE_CONTENT) } returns ApiResult.Success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onReportReasonSelected(target.id, ReportReason.INAPPROPRIATE_CONTENT)
        vm.confirmReport()
        advanceUntilIdle()

        verify(exactly = 1) {
            analyticsClient.log(match { it.name == "feed_report_result" && it.params["outcome"] == AnalyticsParamValue.StringValue("success") })
        }
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

    // ---- 20. cache gol + succes cu postari, emisia cache-ului intarziata -> fara Empty tranzitoriu ----

    @Test
    fun `succes cu postari nu trece tranzitoriu prin Empty cand emisia cache-ului e intarziata`() = runTest {
        feedCache = FakeFeedCache(emissionScope = this, emissionDelayMs = 50)
        networkAvailable.value = true
        internetValidated.value = true
        val freshPost = post()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(freshPost), hasMore = false))

        val vm = createViewModel()
        val contents = collectContent(vm)
        advanceUntilIdle()

        assertFalse("continutul nu ar trebui sa treaca niciodata prin Empty", contents.any { it is FeedContent.Empty })
        contents.assertNoTransientEmpty()
        assertEquals(FeedContent.Posts, contents.last())
    }

    // ---- 21. cache gol + succes cu zero postari -> Empty doar ca stare finala ----

    @Test
    fun `succes cu zero postari ajunge la Empty fara pas intermediar gresit`() = runTest {
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = emptyList(), hasMore = false))

        val vm = createViewModel()
        val contents = collectContent(vm)
        advanceUntilIdle()

        assertEquals(FeedContent.Empty, contents.last())
        contents.assertNoTransientEmpty()
        assertTrue(
            "Empty trebuie precedat doar de Skeletons",
            contents.dropLast(1).all { it is FeedContent.Skeletons },
        )
    }

    // ---- 22. cache nevid + offline -> fara Empty sau NoInternet in secventa ----

    @Test
    fun `cache nevid si offline la startup nu trece niciodata prin Empty sau NoInternet`() = runTest {
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        // networkAvailable/internetValidated stay false (offline at startup).

        val vm = createViewModel()
        val contents = collectContent(vm)
        advanceUntilIdle()

        assertFalse(contents.any { it is FeedContent.Empty })
        assertFalse(contents.any { it is FeedContent.NoInternet })
        assertEquals(FeedContent.Posts, contents.last())
    }

    // ---- 23. refresh esuat peste postari existente -> content ramane Posts tot timpul ----

    @Test
    fun `refresh esuat peste postari existente nu paraseste niciodata Posts`() = runTest {
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
        val contents = collectContent(vm)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf(FeedContent.Posts), contents.distinct())
    }

    // ---- 24. owner mismatch -> secventa nu expune niciodata postarile vechi ----

    @Test
    fun `owner mismatch nu expune niciodata postarile vechi in secventa de continut`() = runTest {
        val otherOwner = UUID.randomUUID()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = otherOwner,
            syncedAt = Instant.now(),
        )

        val vm = createViewModel()
        val contents = collectContent(vm)
        advanceUntilIdle()

        assertFalse("postarile altui user nu trebuie randate niciodata", contents.any { it is FeedContent.Posts })
        assertEquals(FeedContent.NoInternet, contents.last())
    }

    // ---- 25. shimmer infinit la prima instalare — verificarea din planul de implementare ----

    @Test
    fun `loadNextPage inainte de hidratare nu porneste nicio cerere si nu blocheaza prima pagina`() = runTest {
        val delayedUserId = flow {
            delay(100)
            emit(ownerUserId)
        }
        userPreferences = mockk {
            every { userId } returns delayedUserId
        }
        networkAvailable.value = true
        internetValidated.value = true
        val freshPost = post()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(freshPost), hasMore = false))

        val vm = createViewModel()
        // Hydration hasn't resolved ownerUserId yet at this point — phase is still HydratingCache.
        vm.loadNextPage()
        advanceUntilIdle()

        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertEquals(FeedPhase.ShowingPosts, vm.uiState.value.phase)
        coVerify(exactly = 1) { postRepository.getFeedPosts(limit = 15, cursor = null) }
    }

    @Test
    fun `cache gol si prima pagina cu postari ajunge la content Posts`() = runTest {
        networkAvailable.value = true
        internetValidated.value = true
        val freshPost = post()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(freshPost), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertEquals(listOf(freshPost), vm.uiState.value.feedPosts)
    }

    @Test
    fun `cache gol si prima pagina goala ajunge la content Empty nu Skeletons`() = runTest {
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = emptyList(), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(FeedContent.Empty, vm.uiState.value.content)
        assertEquals(FeedPhase.ConfirmedEmpty, vm.uiState.value.phase)
    }

    @Test
    fun `prima pagina cu eroare de server ajunge la FirstPageFailed si content Error nu Skeletons`() = runTest {
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Error("Server error")

        val vm = createViewModel()
        advanceUntilIdle()

        val phase = vm.uiState.value.phase
        assertTrue("faza ar trebui sa fie FirstPageFailed", phase is FeedPhase.FirstPageFailed)
        assertTrue(
            "continutul ar trebui sa fie Error, nu Skeletons",
            vm.uiState.value.content is FeedContent.Error,
        )
    }

    @Test
    fun `ownerUserId nerezolvat nu impiedica postarile sa ajunga in cache si pe ecran`() = runTest {
        userPreferences = mockk {
            every { userId } returns MutableStateFlow<UUID?>(null)
        }
        networkAvailable.value = true
        internetValidated.value = true
        val freshPost = post()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(freshPost), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertEquals(listOf(freshPost), vm.uiState.value.feedPosts)
        assertEquals(listOf(freshPost), feedCache.observePosts().first())
    }

    @Test
    fun `hydrateFromCache cu cache care arunca se recupereaza prin loadFirstPage`() = runTest {
        feedCache.failNextReadMeta = true
        networkAvailable.value = true
        internetValidated.value = true
        val freshPost = post()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(freshPost), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertEquals(listOf(freshPost), vm.uiState.value.feedPosts)
    }

    // ---- 26. visiblePosts (pasul 4 din planul de implementare) ----

    @Test
    fun `visiblePosts contine doar postarile ale caror imagini sunt cached sau prefetch-uite cu succes`() = runTest {
        val visible = post()
        val hidden = post()
        feedImagePrefetcher.cachedUrls += visible.imageUrl
        feedImagePrefetcher.outcomes[hidden.imageUrl] = PrefetchOutcome.PermanentFailure("404")

        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(visible, hidden), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(visible, hidden), vm.uiState.value.feedPosts)
        assertEquals(listOf(visible.id), vm.uiState.value.visiblePosts.map { it.id })
    }

    @Test
    fun `replaceWithFirstPage (refresh) reseteaza visiblePosts la noua pagina`() = runTest {
        val originalPost = post()
        feedImagePrefetcher.cachedUrls += originalPost.imageUrl
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(originalPost), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(listOf(originalPost.id), vm.uiState.value.visiblePosts.map { it.id })

        val refreshedPost = post()
        feedImagePrefetcher.cachedUrls += refreshedPost.imageUrl
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(refreshedPost), hasMore = false))

        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf(refreshedPost.id), vm.uiState.value.visiblePosts.map { it.id })
    }

    @Test
    fun `appendPage (loadNextPage) doar extinde visiblePosts`() = runTest {
        val firstPost = post()
        feedImagePrefetcher.cachedUrls += firstPost.imageUrl
        val cursor = FeedCursor(lastCreatedAt = Instant.now(), lastPostId = UUID.randomUUID())
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(firstPost), nextCursor = cursor, hasMore = true),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(listOf(firstPost.id), vm.uiState.value.visiblePosts.map { it.id })

        val secondPost = post()
        feedImagePrefetcher.cachedUrls += secondPost.imageUrl
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = cursor) } returns
            ApiResult.Success(feedResult(posts = listOf(secondPost), hasMore = false))

        vm.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(firstPost.id, secondPost.id), vm.uiState.value.visiblePosts.map { it.id })
    }

    @Test
    fun `owner mismatch goleste visiblePosts`() = runTest {
        val otherOwner = UUID.randomUUID()
        val staleVisiblePost = post()
        feedImagePrefetcher.cachedUrls += staleVisiblePost.imageUrl
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(staleVisiblePost), hasMore = false),
            ownerUserId = otherOwner,
            syncedAt = Instant.now(),
        )

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, feedCache.clearCount)
        assertTrue(vm.uiState.value.visiblePosts.isEmpty())
    }

    // ---- 27. stari terminale de gating (pasul 6 din planul de implementare) ----

    // E10: o pagina intreaga fara imagini utilizabile.
    @Test
    fun `o pagina intreaga fara imagini utilizabile ajunge la Error cu retry functional, fara skeleton infinit`() = runTest {
        val posts = listOf(post(), post(), post())
        posts.forEach { feedImagePrefetcher.outcomes[it.imageUrl] = PrefetchOutcome.PermanentFailure("404") }
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = posts, hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.visiblePosts.isEmpty())
        assertTrue(
            "continutul ar trebui sa fie Error, nu ramas blocat pe Skeletons",
            vm.uiState.value.content is FeedContent.Error,
        )

        // Retry functional: o noua incercare, de data asta cu imagini care se incarca, recupereaza feedul.
        // Postari noi (id-uri noi) — FakeFeedCache e un MutableStateFlow: re-emiterea acelorasi
        // postari (egale structural) nu ar declansa un nou collect, exact ca-n productie Room
        // nu ar fi nevoie, dar simularea trebuie sa reflecte o emisie noua reala.
        val recoveredPosts = listOf(post(), post(), post())
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = recoveredPosts, hasMore = false))
        vm.onInitialRetry()
        advanceUntilIdle()

        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertEquals(recoveredPosts.map { it.id }, vm.uiState.value.visiblePosts.map { it.id })
        coVerify(exactly = 2) { postRepository.getFeedPosts(limit = 15, cursor = null) }
    }

    // E6: R2_PUBLIC_BASE_URL invalid — toate URL-urile 404, refill-ul se epuizeaza dupa 2 pagini suplimentare.
    @Test
    fun `toate imaginile 404 dupa un R2 base URL invalid epuizeaza refill-ul si ajunge la Error`() = runTest {
        val page1 = listOf(post(), post())
        page1.forEach { feedImagePrefetcher.outcomes[it.imageUrl] = PrefetchOutcome.PermanentFailure("404") }
        val cursor1 = FeedCursor(lastCreatedAt = Instant.now(), lastPostId = UUID.randomUUID())
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = page1, nextCursor = cursor1, hasMore = true))

        val vm = createViewModel()
        advanceUntilIdle()

        // First page landed but nothing usable yet — there's still more to try, not an error yet.
        assertTrue(vm.uiState.value.visiblePosts.isEmpty())
        assertFalse(vm.uiState.value.content is FeedContent.Error)

        val page2 = listOf(post(), post())
        page2.forEach { feedImagePrefetcher.outcomes[it.imageUrl] = PrefetchOutcome.PermanentFailure("404") }
        val cursor2 = FeedCursor(lastCreatedAt = Instant.now(), lastPostId = UUID.randomUUID())
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = cursor1) } returns
            ApiResult.Success(feedResult(posts = page2, nextCursor = cursor2, hasMore = true))

        vm.loadNextPage()
        advanceUntilIdle()

        val page3 = listOf(post(), post())
        page3.forEach { feedImagePrefetcher.outcomes[it.imageUrl] = PrefetchOutcome.PermanentFailure("404") }
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = cursor2) } returns
            ApiResult.Success(feedResult(posts = page3, hasMore = false))

        vm.loadNextPage()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.visiblePosts.isEmpty())
        assertTrue(
            "continutul ar trebui sa fie Error dupa epuizarea refill-ului, nu ramas blocat pe Skeletons",
            vm.uiState.value.content is FeedContent.Error,
        )

        // Retry functional: un refresh cu un R2 base URL bun recupereaza feedul.
        val recoveredPost = post()
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(recoveredPost), hasMore = false))
        vm.onInitialRetry()
        advanceUntilIdle()

        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        assertEquals(listOf(recoveredPost.id), vm.uiState.value.visiblePosts.map { it.id })
    }

    // ----------------------------------------------------------------------
    // pas 2.6a — ev. 21: feed_first_content, cache vs network
    // ----------------------------------------------------------------------

    @Test
    fun `cache nevid la startup - feed_first_content cu source cache`() = runTest {
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )

        createViewModel()
        advanceUntilIdle()

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "feed_first_content",
                    params = mapOf(
                        "source" to AnalyticsParamValue.StringValue("cache"),
                        "duration_bucket" to AnalyticsParamValue.StringValue("lt_1s"),
                    ),
                )
            )
        }
    }

    @Test
    fun `cache gol si online la startup - feed_first_content cu source network`() = runTest {
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(post())))

        createViewModel()
        advanceUntilIdle()

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "feed_first_content",
                    params = mapOf(
                        "source" to AnalyticsParamValue.StringValue("network"),
                        "duration_bucket" to AnalyticsParamValue.StringValue("lt_1s"),
                    ),
                )
            )
        }
    }

    @Test
    fun `cache gol si offline la startup - fara feed_first_content, nu exista continut`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(FeedContent.NoInternet, vm.uiState.value.content)
        verify(exactly = 0) { analyticsClient.log(match { it.name == "feed_first_content" }) }
    }

    @Test
    fun `feed_first_content se declanseaza o singura data chiar daca sosesc pagini suplimentare`() = runTest {
        val cursor = FeedCursor(lastCreatedAt = Instant.now(), lastPostId = UUID.randomUUID())
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), nextCursor = cursor, hasMore = true),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = cursor) } returns
            ApiResult.Success(feedResult(posts = listOf(post()), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFooterRetry()
        advanceUntilIdle()

        verify(exactly = 1) { analyticsClient.log(match { it.name == "feed_first_content" }) }
    }

    // ----------------------------------------------------------------------
    // pas 2.6b — ev. 22: feed_load_result, cele 5 triggere + ramura isSilent
    // ----------------------------------------------------------------------

    private fun feedLoadResultEvent(trigger: String, outcome: String) = AnalyticsEvent(
        name = "feed_load_result",
        params = mapOf(
            "trigger" to AnalyticsParamValue.StringValue(trigger),
            "outcome" to AnalyticsParamValue.StringValue(outcome),
        ),
    )

    @Test
    fun `initial - feed_load_result trigger initial outcome success`() = runTest {
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(post())))

        createViewModel()
        advanceUntilIdle()

        verify(exactly = 1) { analyticsClient.log(feedLoadResultEvent("initial", "success")) }
    }

    @Test
    fun `refresh - feed_load_result trigger refresh outcome success`() = runTest {
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(post()), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        verify(exactly = 1) { analyticsClient.log(feedLoadResultEvent("refresh", "success")) }
    }

    @Test
    fun `loadNextPage - feed_load_result trigger load_more outcome success`() = runTest {
        val cursor = FeedCursor(lastCreatedAt = Instant.now(), lastPostId = UUID.randomUUID())
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), nextCursor = cursor, hasMore = true),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = cursor) } returns
            ApiResult.Success(feedResult(posts = listOf(post()), hasMore = false))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.loadNextPage()
        advanceUntilIdle()

        // FeedImageGate poate cere și el singur un refill (onRefillNeeded -> loadNextPage(),
        // același trigger "load_more") — nu contează care apel a produs efectiv evenimentul,
        // doar că trigger-ul "load_more" e cel corect pentru această cale.
        verify(atLeast = 1) { analyticsClient.log(feedLoadResultEvent("load_more", "success")) }
    }

    @Test
    fun `onFooterRetry - feed_load_result trigger footer_retry`() = runTest {
        val cursor = FeedCursor(lastCreatedAt = Instant.now(), lastPostId = UUID.randomUUID())
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), nextCursor = cursor, hasMore = true),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        // Vezi comentariul din testul anterior — hasMore rămâne true ca apelul explicit de mai
        // jos să nu fie blocat de un eventual refill automat al FeedImageGate.
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = cursor) } returns
            ApiResult.Success(feedResult(posts = listOf(post()), nextCursor = cursor, hasMore = true))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.onFooterRetry()
        advanceUntilIdle()

        verify(exactly = 1) { analyticsClient.log(feedLoadResultEvent("footer_retry", "success")) }
    }

    @Test
    fun `sync silentios reusit - feed_load_result trigger silent_sync outcome success`() = runTest {
        // Online chiar de la construcție: hydrateFromCache() găsește cache-ul stale și cheamă
        // maybeSyncSilently() direct, fără nicio tranziție offline->reconnect (acea cale trece
        // prin FeedImageGate și e deja instabilă în acest harness de test, independent de 2.6b).
        val staleSyncedAt = Instant.now().minus(Duration.ofMinutes(31))
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = staleSyncedAt,
        )
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Success(feedResult(posts = listOf(post()), hasMore = false))

        createViewModel()
        advanceUntilIdle()

        verify(exactly = 1) { analyticsClient.log(feedLoadResultEvent("silent_sync", "success")) }
    }

    @Test
    fun `sync silentios esuat - devine vizibil ca feed_load_result, desi UI ramane tacut`() = runTest {
        // Online chiar de la construcție — vezi comentariul din testul anterior.
        val staleSyncedAt = Instant.now().minus(Duration.ofMinutes(31))
        val originalPost = post()
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(originalPost), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = staleSyncedAt,
        )
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Error("Server error", code = "SERVER_ERROR")

        val vm = createViewModel()
        advanceUntilIdle()

        // UI-ul rămâne complet tăcut (comportament neschimbat)...
        assertNull(vm.uiState.value.userMessage)
        assertNull(vm.uiState.value.refreshError)
        // ...dar evenimentul acum există, cu failure_code inclus.
        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "feed_load_result",
                    params = mapOf(
                        "trigger" to AnalyticsParamValue.StringValue("silent_sync"),
                        "outcome" to AnalyticsParamValue.StringValue("failure"),
                        "failure_code" to AnalyticsParamValue.StringValue("SERVER_ERROR"),
                    ),
                )
            )
        }
    }

    @Test
    fun `esec initial - feed_load_result cu failure_code`() = runTest {
        networkAvailable.value = true
        internetValidated.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns
            ApiResult.Error("Server error", code = "SERVER_ERROR")

        createViewModel()
        advanceUntilIdle()

        verify(exactly = 1) {
            analyticsClient.log(
                AnalyticsEvent(
                    name = "feed_load_result",
                    params = mapOf(
                        "trigger" to AnalyticsParamValue.StringValue("initial"),
                        "outcome" to AnalyticsParamValue.StringValue("failure"),
                        "failure_code" to AnalyticsParamValue.StringValue("SERVER_ERROR"),
                    ),
                )
            )
        }
    }

    // ----------------------------------------------------------------------
    // pas 5 — onPostRemovedByAdmin: elimină postarea din feedPosts/visiblePosts fără rețea
    // ----------------------------------------------------------------------

    @Test
    fun `onPostRemovedByAdmin scoate postarea din feedPosts si visiblePosts fara retea`() = runTest {
        val removed = post()
        val other = post()
        feedImagePrefetcher.cachedUrls += removed.imageUrl
        feedImagePrefetcher.cachedUrls += other.imageUrl
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(removed, other), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(setOf(removed.id, other.id), vm.uiState.value.visiblePosts.map { it.id }.toSet())

        vm.onPostRemovedByAdmin(removed.id)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.feedPosts.none { it.id == removed.id })
        assertTrue(vm.uiState.value.visiblePosts.none { it.id == removed.id })
        assertTrue(vm.uiState.value.visiblePosts.any { it.id == other.id })
    }

    @Test
    fun `onPostRemovedByAdmin sterge postarea din cache-ul persistent`() = runTest {
        val removed = post()
        feedImagePrefetcher.cachedUrls += removed.imageUrl
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(removed), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onPostRemovedByAdmin(removed.id)
        advanceUntilIdle()

        // Simulates a restart reading straight from the cache — the post must not revive.
        assertTrue(feedCache.observePosts().first().none { it.id == removed.id })
    }

    // ----------------------------------------------------------------------
    // pas 3 (docs/plans/avem-un-bug-android-mutable-sky.md) — onResumed() retries a feed stuck
    // in a network-error state without depending on any connectivity transition.
    // ----------------------------------------------------------------------

    @Test
    fun `onResumed din NoInternet declanseaza exact un load si arata postarile`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(FeedContent.NoInternet, vm.uiState.value.content)

        val result = feedResult(posts = listOf(post()))
        networkAvailable.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = null) } returns ApiResult.Success(result)

        vm.onResumed()
        advanceUntilIdle()

        assertEquals(FeedContent.Posts, vm.uiState.value.content)
        coVerify(exactly = 1) { postRepository.getFeedPosts(limit = 15, cursor = null) }
    }

    @Test
    fun `onResumed cu loadMoreError declanseaza exact un load si il curata`() = runTest {
        val cachedPost = post()
        feedImagePrefetcher.cachedUrls += cachedPost.imageUrl
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(cachedPost), hasMore = true),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        val vm = createViewModel()
        advanceUntilIdle()

        // Same setup as "footer retry offline" — the first tap is what actually produces the
        // OfflineRetry state (load() sets loadMoreError after the offline pre-flight check).
        vm.onFooterRetry()
        advanceUntilIdle()
        assertEquals(FeedFooterState.OfflineRetry, vm.uiState.value.footer)

        val cursor = feedCache.readMeta()?.nextCursor
        networkAvailable.value = true
        coEvery { postRepository.getFeedPosts(limit = 15, cursor = cursor) } returns
            ApiResult.Success(feedResult(posts = listOf(post()), hasMore = false))

        vm.onResumed()
        advanceUntilIdle()

        assertEquals(FeedFooterState.CaughtUp, vm.uiState.value.footer)
        coVerify(exactly = 1) { postRepository.getFeedPosts(limit = 15, cursor = cursor) }
    }

    @Test
    fun `onResumed nu face nimic cand feedul nu e in eroare`() = runTest {
        feedCache.replaceWithFirstPage(
            page = feedResult(posts = listOf(post()), hasMore = false),
            ownerUserId = ownerUserId,
            syncedAt = Instant.now(),
        )
        networkAvailable.value = true
        internetValidated.value = true
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onResumed()
        advanceUntilIdle()

        coVerify(exactly = 0) { postRepository.getFeedPosts(any(), any()) }
    }
}
