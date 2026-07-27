package com.revio.app.features.profile.dashboard

import androidx.lifecycle.SavedStateHandle
import com.revio.app.MainDispatcherRule
import com.revio.app.core.navigation.Screen
import com.revio.app.core.network.ApiResult
import com.revio.app.data.local.preferences.UserPreferences
import com.revio.app.data.model.Comment
import com.revio.app.data.model.FeedPost
import com.revio.app.data.model.LikeStatus
import com.revio.app.data.model.User
import com.revio.app.data.remote.dto.post.FeedResult
import com.revio.app.data.repository.CommentRepository
import com.revio.app.data.repository.LikeRepository
import com.revio.app.data.repository.PostRepository
import com.revio.app.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileDashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository: UserRepository = mockk()
    private val postRepository: PostRepository = mockk()
    private val likeRepository: LikeRepository = mockk(relaxed = true)
    private val commentRepository: CommentRepository = mockk(relaxed = true)
    private val userPreferences: UserPreferences = mockk()

    private val currentUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val foreignUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    private fun currentUser(streakDays: Int = 0) = User(
        id = currentUserId,
        fullName = "Current User",
        username = "current_user",
        country = "Romania",
        streakDays = streakDays,
    )

    private fun foreignUser(streakDays: Int = 0) = User(
        id = foreignUserId,
        fullName = "Foreign User",
        username = "foreign_user",
        country = "France",
        streakDays = streakDays,
    )

    private fun emptyFeedResult() = FeedResult(
        posts = emptyList(),
        nextCursor = null,
        hasMore = false,
    )

    private fun feedPost(id: UUID = UUID.randomUUID(), userId: UUID = foreignUserId) = FeedPost(
        id = id,
        userId = userId,
        username = "someone",
        brand = "BMW",
        model = "M3",
        imageUrl = "https://example.com/img.jpg",
        caption = null,
        latitude = null,
        longitude = null,
        createdAt = Instant.now(),
        likeCount = 0,
        commentCount = 0,
        likedByCurrentUser = false,
    )

    private fun fakeComment(postId: UUID, text: String = "nice!") = Comment(
        id = UUID.randomUUID(),
        userId = foreignUserId,
        postId = postId,
        username = "someone",
        profilePictureUrl = null,
        text = text,
        createdAt = Instant.now(),
    )

    private fun ownerSavedStateHandle() = SavedStateHandle()

    private fun foreignSavedStateHandle(userId: UUID = foreignUserId) =
        SavedStateHandle(mapOf(Screen.Profile.ARG_USER_ID to userId.toString()))

    private fun invalidSavedStateHandle() =
        SavedStateHandle(mapOf(Screen.Profile.ARG_USER_ID to "not-a-uuid"))

    @Before
    fun setUp() {
        every { userPreferences.userId } returns flowOf(currentUserId)
        every { userRepository.currentUser } returns MutableStateFlow(null)
    }

    private fun buildVm(savedStateHandle: SavedStateHandle) = ProfileDashboardViewModel(
        savedStateHandle = savedStateHandle,
        userRepository = userRepository,
        postRepository = postRepository,
        likeRepository = likeRepository,
        commentRepository = commentRepository,
        userPreferences = userPreferences,
    )

    // ── Owner flow ────────────────────────────────────────────────────────────

    @Test
    fun `userId null - flux owner, getCurrentUser apelat, isOwnProfile true`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser())
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isOwnProfile)
        assertEquals(currentUserId, state.currentUserId)
        assertEquals(currentUserId, state.user?.id)
        coVerify(exactly = 1) { userRepository.getCurrentUser() }
        coVerify(exactly = 0) { userRepository.getUserById(any()) }
    }

    // ── Foreign flow ──────────────────────────────────────────────────────────

    @Test
    fun `userId strain valid - getUserById apelat, isOwnProfile false`() = runTest {
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isOwnProfile)
        assertEquals(currentUserId, state.currentUserId)
        assertEquals(foreignUserId, state.user?.id)
        coVerify(exactly = 1) { userRepository.getUserById(foreignUserId) }
        coVerify(exactly = 0) { userRepository.getCurrentUser() }
    }

    @Test
    fun `userId egal cu currentUserId - isOwnProfile true`() = runTest {
        coEvery { userRepository.getUserById(currentUserId) } returns ApiResult.Success(currentUser())
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(foreignSavedStateHandle(currentUserId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isOwnProfile)
    }

    // ── UUID invalid ──────────────────────────────────────────────────────────

    @Test
    fun `UUID invalid in SavedStateHandle - errorMessage setat, niciun apel la retea`() = runTest {
        val vm = buildVm(invalidSavedStateHandle())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.errorMessage)
        assertNull(state.user)
        coVerify(exactly = 0) { userRepository.getUserById(any()) }
        coVerify(exactly = 0) { userRepository.getCurrentUser() }
        coVerify(exactly = 0) { postRepository.getUserPosts(any(), any(), any()) }
    }

    // ── Erori API ─────────────────────────────────────────────────────────────

    @Test
    fun `getUserById 404 - errorMessage setat, user null, postari nu se incarca`() = runTest {
        coEvery { userRepository.getUserById(foreignUserId) } returns
            ApiResult.Error("User not found")

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.errorMessage)
        assertNull(state.user)
        assertFalse(state.isLoadingUser)
        coVerify(exactly = 0) { postRepository.getUserPosts(any(), any(), any()) }
    }

    @Test
    fun `profil incarcat cu succes dar postari esuate - user prezent, errorMessage setat`() = runTest {
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Error("Server error")

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.user)
        assertEquals(foreignUserId, state.user?.id)
        assertNotNull(state.errorMessage)
        assertTrue(state.posts.isEmpty())
    }

    // ── Gate delete pe isOwnProfile ───────────────────────────────────────────

    @Test
    fun `requestDeletePost si confirmDeletePost sunt no-op pe profil strain`() = runTest {
        val postId = UUID.randomUUID()
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(
                posts = listOf(feedPost(id = postId)),
                nextCursor = null,
                hasMore = false,
            ))

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        coEvery { postRepository.getPostDetail(postId) } returns ApiResult.Success(feedPost(id = postId))

        vm.onPostClick(postId)
        vm.requestDeletePost()
        assertFalse(vm.uiState.value.showDeleteConfirm)

        vm.confirmDeletePost()
        coVerify(exactly = 0) { postRepository.deletePost(any()) }
    }

    // ── Streak ────────────────────────────────────────────────────────────────

    @Test
    fun `user cu streakDays pozitiv - uiState streakDays reflecta valoarea`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser(streakDays = 5))
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        assertEquals(5, vm.uiState.value.streakDays)
    }

    @Test
    fun `user cu streakDays zero - uiState streakDays este zero`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser(streakDays = 0))
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.streakDays)
    }

    @Test
    fun `user null - uiState streakDays fallback la zero`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Error("Network error")
        coEvery { postRepository.getUserPosts(any(), any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        assertNull(vm.uiState.value.user)
        assertEquals(0, vm.uiState.value.streakDays)
    }

    // ── Refresh și paginare ───────────────────────────────────────────────────

    @Test
    fun `refresh pe profil strain reapeleaza getUserPosts cu foreignUserId`() = runTest {
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        coVerify(exactly = 2) { postRepository.getUserPosts(foreignUserId, any(), any()) }
        coVerify(exactly = 0) { postRepository.getUserPosts(currentUserId, any(), any()) }
    }

    @Test
    fun `loadNextPage pe profil strain apeleaza getUserPosts cu foreignUserId`() = runTest {
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(
                posts = emptyList(),
                nextCursor = null,
                hasMore = true,
            ))

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        vm.loadNextPage()
        advanceUntilIdle()

        coVerify(exactly = 2) { postRepository.getUserPosts(foreignUserId, any(), any()) }
    }

    // ── Restanțe: refresh coordonat & onPostCreated (pașii 1–7) ─────────────

    @Test
    fun `pull-to-refresh pe profil propriu apeleaza getCurrentUser si getUserPosts`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser())
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        coVerify(exactly = 2) { userRepository.getCurrentUser() }
        coVerify(exactly = 2) { postRepository.getUserPosts(currentUserId, any(), any()) }
    }

    @Test
    fun `pull-to-refresh pe profil strain apeleaza getUserById si getUserPosts, niciodata getCurrentUser`() = runTest {
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        coVerify(exactly = 2) { userRepository.getUserById(foreignUserId) }
        coVerify(exactly = 2) { postRepository.getUserPosts(foreignUserId, any(), any()) }
        coVerify(exactly = 0) { userRepository.getCurrentUser() }
    }

    @Test
    fun `onPostCreated actualizeaza spotScore, postCount, streakDays si posts`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser())
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        val updatedUser = currentUser(streakDays = 1).copy(spotScore = 10, postCount = 1)
        val newPost = feedPost(userId = currentUserId)
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(updatedUser)
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(newPost), nextCursor = null, hasMore = false))

        vm.onPostCreated()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(10, state.user?.spotScore)
        assertEquals(1, state.postCount)
        assertEquals(1, state.streakDays)
        assertEquals(listOf(newPost.id), state.posts.map { it.id })
    }

    @Test
    fun `isRefreshing ramane true pana cand ambele apeluri s-au incheiat`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser())
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        val userDeferred = CompletableDeferred<ApiResult<User>>()
        val postsDeferred = CompletableDeferred<ApiResult<FeedResult>>()
        coEvery { userRepository.getCurrentUser() } coAnswers { userDeferred.await() }
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } coAnswers { postsDeferred.await() }

        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isRefreshing)

        userDeferred.complete(ApiResult.Success(currentUser()))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isRefreshing)

        postsDeferred.complete(ApiResult.Success(emptyFeedResult()))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `refresh - profil ok si postari eroare - statistici noi pastrate, postari vechi intacte, errorMessage setat`() = runTest {
        val oldPost = feedPost(userId = currentUserId)
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser())
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(oldPost), nextCursor = null, hasMore = false))

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser(streakDays = 3))
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Error("Server error")

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(3, state.streakDays)
        assertEquals(listOf(oldPost.id), state.posts.map { it.id })
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `refresh - postari ok si profil eroare - postari noi pastrate, statistici vechi vizibile, userMessage setat`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser(streakDays = 2))
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        val newPost = feedPost(userId = currentUserId)
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Error("Server error")
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(newPost), nextCursor = null, hasMore = false))

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(newPost.id), state.posts.map { it.id })
        assertEquals(2, state.streakDays)
        assertNotNull(state.userMessage)
    }

    @Test
    fun `doua refresh-uri suprapuse - rezultatul vechi nu suprascrie rezultatul nou`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser())
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        val staleUserDeferred = CompletableDeferred<ApiResult<User>>()
        coEvery { userRepository.getCurrentUser() } coAnswers { staleUserDeferred.await() }

        vm.refresh() // first refresh gets stuck awaiting the profile fetch
        runCurrent()

        val freshUser = currentUser(streakDays = 9)
        val freshPost = feedPost(userId = currentUserId)
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(freshUser)
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(freshPost), nextCursor = null, hasMore = false))

        vm.refresh() // cancels the first refresh's job before it can land
        advanceUntilIdle()

        // The stale response arrives after cancellation — must have no effect.
        staleUserDeferred.complete(ApiResult.Success(currentUser(streakDays = 1)))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(9, state.streakDays)
        assertEquals(listOf(freshPost.id), state.posts.map { it.id })
    }

    @Test
    fun `doua post_created rapide - stare consistenta`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser())
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        val staleUserDeferred = CompletableDeferred<ApiResult<User>>()
        coEvery { userRepository.getCurrentUser() } coAnswers { staleUserDeferred.await() }

        vm.onPostCreated() // first call gets stuck awaiting the profile fetch
        runCurrent()

        val freshUser = currentUser(streakDays = 4).copy(postCount = 2)
        val freshPost = feedPost(userId = currentUserId)
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(freshUser)
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(freshPost), nextCursor = null, hasMore = false))

        vm.onPostCreated() // cancels the first call's job before it can land
        advanceUntilIdle()

        staleUserDeferred.complete(ApiResult.Success(currentUser(streakDays = 1)))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(4, state.streakDays)
        assertEquals(2, state.postCount)
        assertEquals(listOf(freshPost.id), state.posts.map { it.id })
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `tranzitia din gol - postCount, spotScore, streakDays si posts trec de la zero la valori noi`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns
            ApiResult.Success(currentUser(streakDays = 0).copy(spotScore = 0, postCount = 0))
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        val initial = vm.uiState.value
        assertEquals(0, initial.postCount)
        assertEquals(0, initial.user?.spotScore)
        assertEquals(0, initial.streakDays)
        assertTrue(initial.posts.isEmpty())

        val newPost = feedPost(userId = currentUserId)
        coEvery { userRepository.getCurrentUser() } returns
            ApiResult.Success(currentUser(streakDays = 1).copy(spotScore = 10, postCount = 1))
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(newPost), nextCursor = null, hasMore = false))

        vm.onPostCreated()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.postCount)
        assertEquals(10, state.user?.spotScore)
        assertEquals(1, state.streakDays)
        assertEquals(listOf(newPost.id), state.posts.map { it.id })
    }

    @Test
    fun `repro bug - user cu 0 postari - onPostCreated schimba toate cele trei statistici`() = runTest {
        coEvery { userRepository.getCurrentUser() } returns
            ApiResult.Success(currentUser(streakDays = 0).copy(spotScore = 0, postCount = 0))
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        coEvery { userRepository.getCurrentUser() } returns
            ApiResult.Success(currentUser(streakDays = 1).copy(spotScore = 5, postCount = 1))

        vm.onPostCreated()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.postCount)
        assertEquals(5, state.user?.spotScore)
        assertEquals(1, state.streakDays)
    }

    // ── Calea de push prin userRepository.currentUser (pasul 9.8) ────────────

    @Test
    fun `emiterea unui user pe currentUser actualizeaza spotScore, postCount, streakDays fara apel getCurrentUser`() = runTest {
        val currentUserFlow = MutableStateFlow<User?>(null)
        every { userRepository.currentUser } returns currentUserFlow
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser())
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        val pushedUser = currentUser(streakDays = 7).copy(spotScore = 33, postCount = 4)
        currentUserFlow.value = pushedUser
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(33, state.user?.spotScore)
        assertEquals(4, state.postCount)
        assertEquals(7, state.streakDays)
        coVerify(exactly = 1) { userRepository.getCurrentUser() }
    }

    @Test
    fun `pe profil strain o emisie pe currentUser nu modifica uiState-ul user`() = runTest {
        val currentUserFlow = MutableStateFlow<User?>(null)
        every { userRepository.currentUser } returns currentUserFlow
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(emptyFeedResult())

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        val stateBeforePush = vm.uiState.value.user
        currentUserFlow.value = currentUser(streakDays = 7).copy(spotScore = 33, postCount = 4)
        advanceUntilIdle()

        assertEquals(stateBeforePush, vm.uiState.value.user)
        assertEquals(foreignUserId, vm.uiState.value.user?.id)
    }

    // ── Retry per imagine (grid tiles) ───────────────────────────────────────

    @Test
    fun `onImageLoadFailed adauga cheia in failedImages, onImageLoadSucceeded o curata din toate colectiile`() = runTest {
        val postId = UUID.randomUUID()
        val post = feedPost(id = postId)
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(post), nextCursor = null, hasMore = false))

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        val key = PostImageKey(postId, post.imageUrl)
        vm.onImageLoadFailed(key)
        assertTrue(key in vm.uiState.value.failedImages)

        vm.retryImage(key)
        assertEquals(1, vm.uiState.value.imageRetryTokens[key])
        assertFalse(key in vm.uiState.value.failedImages)

        vm.onImageLoadSucceeded(key)
        val state = vm.uiState.value
        assertFalse(key in state.failedImages)
        assertFalse(key in state.autoRetriedImages)
        assertFalse(state.imageRetryTokens.containsKey(key))
    }

    @Test
    fun `retryFailedImagesOnce apelat de doua ori consecutiv creste tokenul o singura data`() = runTest {
        val postId = UUID.randomUUID()
        val post = feedPost(id = postId)
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(post), nextCursor = null, hasMore = false))

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        val key = PostImageKey(postId, post.imageUrl)
        vm.onImageLoadFailed(key)

        vm.retryFailedImagesOnce()
        assertEquals(1, vm.uiState.value.imageRetryTokens[key])
        assertTrue(key in vm.uiState.value.autoRetriedImages)

        vm.retryFailedImagesOnce()
        assertEquals(1, vm.uiState.value.imageRetryTokens[key])
    }

    @Test
    fun `retryFailedImagesOnce nu atinge nicio cheie care nu e in failedImages`() = runTest {
        val postId = UUID.randomUUID()
        val post = feedPost(id = postId)
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(post), nextCursor = null, hasMore = false))

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        // Simulates an image that loaded successfully on the first try — never registered as failed.
        val key = PostImageKey(postId, post.imageUrl)
        vm.onImageLoadSucceeded(key)

        vm.retryFailedImagesOnce()

        val state = vm.uiState.value
        assertFalse(state.imageRetryTokens.containsKey(key))
        assertFalse(key in state.autoRetriedImages)
    }

    @Test
    fun `refresh reseteaza imediat toate registrele de imagini`() = runTest {
        val postId = UUID.randomUUID()
        val post = feedPost(id = postId)
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(post), nextCursor = null, hasMore = false))

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        val key = PostImageKey(postId, post.imageUrl)
        vm.onImageLoadFailed(key)
        vm.retryImage(key)
        assertTrue(vm.uiState.value.imageRetryTokens.containsKey(key))

        vm.refresh()

        // Registries are cleared synchronously, before the reload network call even resolves.
        val stateRightAfterCall = vm.uiState.value
        assertTrue(stateRightAfterCall.failedImages.isEmpty())
        assertTrue(stateRightAfterCall.imageRetryTokens.isEmpty())
        assertTrue(stateRightAfterCall.autoRetriedImages.isEmpty())

        advanceUntilIdle()
    }

    @Test
    fun `load elimina din registre cheile ale caror postari nu mai exista in rezultatul nou`() = runTest {
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Error("Server error")

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)

        val survivingPostId = UUID.randomUUID()
        val survivingPost = feedPost(id = survivingPostId)
        val survivingKey = PostImageKey(survivingPostId, survivingPost.imageUrl)
        val goneKey = PostImageKey(UUID.randomUUID(), "https://example.com/gone.jpg")

        vm.onImageLoadFailed(goneKey)
        vm.onImageLoadFailed(survivingKey)
        vm.retryImage(survivingKey)
        assertEquals(setOf(goneKey), vm.uiState.value.failedImages)
        assertTrue(vm.uiState.value.imageRetryTokens.containsKey(survivingKey))

        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(survivingPost), nextCursor = null, hasMore = false))

        // `retry()` doesn't reset the image registries itself — this exercises load()'s own pruning.
        vm.retry()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(survivingPostId), state.posts.map { it.id })
        assertFalse(goneKey in state.failedImages)
        assertTrue(state.imageRetryTokens.containsKey(survivingKey))
    }

    // ── Refresh detaliu postare la deschiderea overlay-ului ─────────────────

    @Test
    fun `onPostClick apeleaza getPostDetail si actualizeaza likeCount, commentCount, likedByCurrentUser`() = runTest {
        val postId = UUID.randomUUID()
        val stalePost = feedPost(id = postId).copy(likeCount = 1, commentCount = 0, likedByCurrentUser = false)
        val freshPost = stalePost.copy(likeCount = 5, commentCount = 3, likedByCurrentUser = true)
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(stalePost), nextCursor = null, hasMore = false))
        coEvery { postRepository.getPostDetail(postId) } returns ApiResult.Success(freshPost)

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        vm.onPostClick(postId)
        advanceUntilIdle()

        val updated = vm.uiState.value.posts.single { it.id == postId }
        assertEquals(5L, updated.likeCount)
        assertEquals(3L, updated.commentCount)
        assertTrue(updated.likedByCurrentUser)
        coVerify(exactly = 1) { postRepository.getPostDetail(postId) }
    }

    @Test
    fun `doua onPostClick consecutive fara raspuns intermediar - un singur apel getPostDetail (dedup)`() = runTest {
        val postId = UUID.randomUUID()
        val post = feedPost(id = postId)
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(post), nextCursor = null, hasMore = false))
        val detailDeferred = CompletableDeferred<ApiResult<FeedPost>>()
        coEvery { postRepository.getPostDetail(postId) } coAnswers { detailDeferred.await() }

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        vm.onPostClick(postId)
        vm.onPostClick(postId)
        detailDeferred.complete(ApiResult.Success(post))
        advanceUntilIdle()

        coVerify(exactly = 1) { postRepository.getPostDetail(postId) }
    }

    @Test
    fun `onPostClick redeschis imediat dupa un fetch reusit - TTL evita un al doilea apel`() = runTest {
        val postId = UUID.randomUUID()
        val post = feedPost(id = postId)
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(post), nextCursor = null, hasMore = false))
        coEvery { postRepository.getPostDetail(postId) } returns ApiResult.Success(post)

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        vm.onPostClick(postId)
        advanceUntilIdle()
        vm.clearSelectedPost()
        vm.onPostClick(postId)
        advanceUntilIdle()

        coVerify(exactly = 1) { postRepository.getPostDetail(postId) }
    }

    @Test
    fun `getPostDetail esueaza - counturile vechi raman, fara userMessage`() = runTest {
        val postId = UUID.randomUUID()
        val post = feedPost(id = postId).copy(likeCount = 2, commentCount = 1, likedByCurrentUser = false)
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(post), nextCursor = null, hasMore = false))
        coEvery { postRepository.getPostDetail(postId) } returns ApiResult.Error("Server error")

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        vm.onPostClick(postId)
        advanceUntilIdle()

        val state = vm.uiState.value
        val unchanged = state.posts.single { it.id == postId }
        assertEquals(2L, unchanged.likeCount)
        assertEquals(1L, unchanged.commentCount)
        assertNull(state.userMessage)
    }

    @Test
    fun `like optimist in zbor cand getPostDetail lent raspunde - valoarea optimista nu e calcata`() = runTest {
        val postId = UUID.randomUUID()
        val post = feedPost(id = postId).copy(likeCount = 1, commentCount = 0, likedByCurrentUser = false)
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(post), nextCursor = null, hasMore = false))
        val detailDeferred = CompletableDeferred<ApiResult<FeedPost>>()
        coEvery { postRepository.getPostDetail(postId) } coAnswers { detailDeferred.await() }
        val likeDeferred = CompletableDeferred<ApiResult<LikeStatus>>()
        coEvery { likeRepository.toggleLike(postId) } coAnswers { likeDeferred.await() }

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        vm.onPostClick(postId)
        vm.onLikeToggle(postId)
        // Stale detail response arrives while the like toggle is still in flight.
        detailDeferred.complete(ApiResult.Success(post.copy(likeCount = 1, likedByCurrentUser = false)))
        advanceUntilIdle()

        val duringLike = vm.uiState.value.posts.single { it.id == postId }
        assertTrue(duringLike.likedByCurrentUser)
        assertEquals(2L, duringLike.likeCount)

        likeDeferred.complete(ApiResult.Success(LikeStatus(liked = true, count = 2)))
        advanceUntilIdle()

        val after = vm.uiState.value.posts.single { it.id == postId }
        assertTrue(after.likedByCurrentUser)
        assertEquals(2L, after.likeCount)
    }

    @Test
    fun `clearSelectedPost inainte ca getPostDetail sa raspunda - fara crash, posts actualizat`() = runTest {
        val postId = UUID.randomUUID()
        val post = feedPost(id = postId)
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(post), nextCursor = null, hasMore = false))
        val detailDeferred = CompletableDeferred<ApiResult<FeedPost>>()
        coEvery { postRepository.getPostDetail(postId) } coAnswers { detailDeferred.await() }

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        vm.onPostClick(postId)
        vm.clearSelectedPost()
        detailDeferred.complete(ApiResult.Success(post.copy(likeCount = 9)))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.selectedPostId)
        assertEquals(9L, state.posts.single { it.id == postId }.likeCount)
    }

    @Test
    fun `post sters cat timp getPostDetail e in zbor - postarea nu reapare`() = runTest {
        val postId = UUID.randomUUID()
        val post = feedPost(id = postId, userId = currentUserId)
        coEvery { userRepository.getCurrentUser() } returns ApiResult.Success(currentUser())
        coEvery { postRepository.getUserPosts(currentUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(post), nextCursor = null, hasMore = false))
        coEvery { postRepository.deletePost(postId) } returns ApiResult.Success(Unit)
        val detailDeferred = CompletableDeferred<ApiResult<FeedPost>>()
        coEvery { postRepository.getPostDetail(postId) } coAnswers { detailDeferred.await() }

        val vm = buildVm(ownerSavedStateHandle())
        advanceUntilIdle()

        vm.onPostClick(postId)
        vm.confirmDeletePost()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.posts.any { it.id == postId })

        detailDeferred.complete(ApiResult.Success(post.copy(likeCount = 9)))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.posts.any { it.id == postId })
    }

    // ── Reconciliere commentCount din comments sheet ─────────────────────────

    @Test
    fun `loadComments success actualizeaza commentCount cu numarul autoritar de comentarii`() = runTest {
        val postId = UUID.randomUUID()
        val post = feedPost(id = postId).copy(commentCount = 0)
        coEvery { userRepository.getUserById(foreignUserId) } returns ApiResult.Success(foreignUser())
        coEvery { postRepository.getUserPosts(foreignUserId, any(), any()) } returns
            ApiResult.Success(FeedResult(posts = listOf(post), nextCursor = null, hasMore = false))
        coEvery { commentRepository.getCommentsForPost(postId) } returns
            ApiResult.Success(listOf(fakeComment(postId), fakeComment(postId)))
        coEvery { postRepository.getPostDetail(postId) } returns ApiResult.Success(post)

        val vm = buildVm(foreignSavedStateHandle())
        advanceUntilIdle()

        vm.openComments(postId)
        advanceUntilIdle()

        assertEquals(2L, vm.uiState.value.posts.single { it.id == postId }.commentCount)
    }
}
