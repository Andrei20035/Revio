package com.revio.app.data.repository

import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.isNetworkError
import com.revio.app.data.remote.api.PostApi
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression guard for the fix that stopped [PostRepositoryImpl] from losing [ApiResult.Error.code]
 * on the feed path — before it, `.getFeedPosts` rebuilt a bare `ApiResult.Error(message)` on the
 * success->domain mapping instead of piping the original error through, so `isNetworkError` was
 * always false and the feed could never tell "offline" apart from a server error.
 */
class PostRepositoryImplTest {

    private lateinit var postApi: PostApi
    private lateinit var repository: PostRepositoryImpl

    @Before
    fun setup() {
        postApi = mockk()
        repository = PostRepositoryImpl(postApi)
    }

    @Test
    fun `getFeedPosts pastreaza codul de eroare de retea`() = runTest {
        coEvery { postApi.getFeedPosts(limit = 15, cursorCreatedAt = null, cursorPostId = null) } throws IOException()

        val result = repository.getFeedPosts(limit = 15, cursor = null)

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).isNetworkError)
    }

    @Test
    fun `getUserPosts pastreaza codul de eroare de retea`() = runTest {
        val userId = java.util.UUID.randomUUID()
        coEvery {
            postApi.getUserPosts(userId = userId, limit = 15, cursorCreatedAt = null, cursorPostId = null)
        } throws IOException()

        val result = repository.getUserPosts(userId = userId, limit = 15, cursor = null)

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).isNetworkError)
    }
}
