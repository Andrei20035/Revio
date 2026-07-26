package com.revio.app.features.feed

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revio.app.features.feed.components.FeedPostSkeleton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresie pentru shimmer-ul infinit la prima instalare (§1 din planul de implementare): cu 3
 * skeletoane afișate ca items reale în `LazyColumn`, prefetch-ul de infinite-scroll declanșa
 * `loadNextPage()` încă din primul layout pass — `total > 0 && lastVisible >= total - 3` devine
 * imediat `true` pentru exact 3 iteme. Reproduce în izolare `shouldLoadMore`/`LaunchedEffect`-ul
 * din `FeedScreen.kt` (fără infra Hilt/NavController), condiționat acum pe `content is
 * FeedContent.Posts`, exact ca în fix.
 */
@RunWith(AndroidJUnit4::class)
class FeedSkeletonPrefetchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var loadNextPageCallCount = 0

    private fun setSkeletonContent(content: FeedContent) {
        loadNextPageCallCount = 0
        composeTestRule.setContent {
            val currentContent = content
            val listState = rememberLazyListState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    if (currentContent !is FeedContent.Posts) return@derivedStateOf false
                    val layoutInfo = listState.layoutInfo
                    val total = layoutInfo.totalItemsCount
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    total > 0 && lastVisible >= total - 3
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) loadNextPageCallCount++
            }

            LazyColumn(state = listState) {
                when (currentContent) {
                    FeedContent.Skeletons -> items(3, key = { "skeleton-$it" }) {
                        FeedPostSkeleton()
                    }
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun `cu_skeletoanele_pe_ecran_loadNextPage_nu_este_declansat`() {
        setSkeletonContent(FeedContent.Skeletons)

        composeTestRule.waitForIdle()

        assertEquals(0, loadNextPageCallCount)
    }
}
