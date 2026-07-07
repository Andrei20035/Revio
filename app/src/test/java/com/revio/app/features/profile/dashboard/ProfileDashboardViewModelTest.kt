package com.revio.app.features.profile.dashboard

import androidx.lifecycle.SavedStateHandle
import com.revio.app.MainDispatcherRule
import com.revio.app.core.navigation.Screen
import com.revio.app.core.network.ApiResult
import com.revio.app.data.local.preferences.UserPreferences
import com.revio.app.data.model.FeedPost
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

    private fun ownerSavedStateHandle() = SavedStateHandle()

    private fun foreignSavedStateHandle(userId: UUID = foreignUserId) =
        SavedStateHandle(mapOf(Screen.Profile.ARG_USER_ID to userId.toString()))

    private fun invalidSavedStateHandle() =
        SavedStateHandle(mapOf(Screen.Profile.ARG_USER_ID to "not-a-uuid"))

    @Before
    fun setUp() {
        every { userPreferences.userId } returns flowOf(currentUserId)
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
}
